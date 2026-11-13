package io.github.kemurixd.lurk.feature.common

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val log = Logger.withTag("LastLiveLoader")

/**
 * Looks up when offline channels were last live, one row at a time, for platforms whose list replies leave the time
 * out (`capabilities.lastLiveLookup`). Screens call [request] when an offline row is composed and [release] when it
 * leaves, so only rows on screen cost a request, and nothing waits on the answer: [times] gains the time when it
 * lands.
 *
 * One instance is shared by every screen. At most [maxParallel] lookups run at once; the rest queue. A queued lookup
 * whose row was released before its turn is dropped without a request, so flinging through a long list doesn't fetch
 * every row it passed. Answers are cached in memory per channel for [ttl], failures for [failureTtl]; a row asked again
 * within that time, or while its lookup is queued or running, costs nothing. A failure keeps the row's previous time,
 * if any. A lookup cancelled with its screen's scope is forgotten and runs again on the next request.
 */
class LastLiveLoader(
    private val platforms: List<StreamingPlatform>,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.System,
    private val ttl: Duration = 10.minutes,
    private val failureTtl: Duration = 1.minutes,
    maxParallel: Int = 3,
) {
    private val permits = Semaphore(maxParallel)
    private val cache = MutableStateFlow(Cache())

    /** The last-live time of every channel looked up so far that has one. */
    val times: Flow<Map<ChannelRef, Instant>> =
        cache
            .map { cache -> cache.entries.mapNotNull { (ref, entry) -> entry.time?.let { ref to it } }.toMap() }
            .distinctUntilChanged()

    /**
     * A row for [ref] is on screen. Looks it up in [scope] unless its answer is fresh, it is already queued or loading,
     * or its platform needs no lookup. Pair each call with one [release].
     */
    fun request(
        ref: ChannelRef,
        scope: CoroutineScope,
    ) {
        val platform = platforms.firstOrNull { it.id == ref.platform && it.capabilities.lastLiveLookup } ?: return
        val now = clock.now()
        var claimed = false
        cache.update { current ->
            val wanted = current.wanted + (ref to (current.wanted[ref] ?: 0) + 1)
            claimed = current.entries[ref]?.isFresh(now) != true && ref !in current.loading
            current.copy(wanted = wanted, loading = if (claimed) current.loading + ref else current.loading)
        }
        if (!claimed) return
        scope.launch {
            try {
                val lookup =
                    permits.withPermit {
                        // Its row may have left the screen while it waited for a permit.
                        if (ref in cache.value.wanted) withContext(dispatchers.io) { lookUp(platform, ref) } else null
                    }
                if (lookup != null) remember(ref, lookup)
            } finally {
                cache.update { it.copy(loading = it.loading - ref) }
            }
        }
    }

    /** A row for [ref] left the screen. Once no row wants it, a lookup still waiting for its turn is dropped. */
    fun release(ref: ChannelRef) {
        cache.update { current ->
            val left = (current.wanted[ref] ?: 0) - 1
            current.copy(wanted = if (left > 0) current.wanted + (ref to left) else current.wanted - ref)
        }
    }

    private fun remember(
        ref: ChannelRef,
        lookup: Lookup,
    ) {
        cache.update { current ->
            val entry =
                when (lookup) {
                    is Lookup.Answer -> Entry(lookup.time, clock.now(), ttl)
                    Lookup.Failed -> Entry(current.entries[ref]?.time, clock.now(), failureTtl)
                }
            current.copy(entries = current.entries + (ref to entry))
        }
    }

    /** The platform's answer, or [Lookup.Failed]. Never throws, except for cancellation. */
    private suspend fun lookUp(
        platform: StreamingPlatform,
        ref: ChannelRef,
    ): Lookup =
        try {
            when (val outcome = platform.lastLive(ref)) {
                is Outcome.Success -> Lookup.Answer(outcome.value)
                is Outcome.Failure -> Lookup.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w { "${ref.platform} last-live lookup failed: ${e::class.simpleName}" }
            Lookup.Failed
        }

    private sealed interface Lookup {
        data class Answer(
            val time: Instant?,
        ) : Lookup

        data object Failed : Lookup
    }

    private data class Entry(
        val time: Instant?,
        val checkedAt: Instant,
        val keepFor: Duration,
    ) {
        fun isFresh(now: Instant): Boolean = now - checkedAt < keepFor
    }

    private data class Cache(
        val entries: Map<ChannelRef, Entry> = emptyMap(),
        val loading: Set<ChannelRef> = emptySet(),
        val wanted: Map<ChannelRef, Int> = emptyMap(),
    )
}

/**
 * These channels with the looked-up [times] filled in, for offline rows whose list reply had no time. A time the
 * reply already had wins.
 */
fun List<Channel>.withLastLive(times: Map<ChannelRef, Instant>): List<Channel> =
    if (times.isEmpty()) {
        this
    } else {
        map { channel ->
            val time = times[channel.ref]
            if (time == null || channel.live != null || channel.lastStream?.startedAt != null) {
                channel
            } else {
                channel.copy(lastStream = (channel.lastStream ?: LastStream(null, null, null)).copy(startedAt = time))
            }
        }
    }

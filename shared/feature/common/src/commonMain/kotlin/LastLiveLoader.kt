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
 * out (`capabilities.lastLiveLookup`). Screens call [request] for each offline row they compose, so only rows on
 * screen cost a request, and nothing waits on the answer: [times] gains the time when it lands.
 *
 * One instance is shared by every screen. Answers are cached in memory per channel for [ttl]; a row asked again
 * within that time, or while its lookup runs, costs nothing. At most [maxParallel] lookups run at once. A failure is
 * cached like an answer, so a row that keeps scrolling past doesn't retry every time, and it leaves the row's
 * previous time, if any. A lookup cancelled with its screen's scope is forgotten and runs again on the next request.
 */
class LastLiveLoader(
    private val platforms: List<StreamingPlatform>,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.System,
    private val ttl: Duration = 10.minutes,
    maxParallel: Int = 3,
) {
    private val permits = Semaphore(maxParallel)
    private val cache = MutableStateFlow(Cache())

    /** The last-live time of every channel looked up so far that has one. */
    val times: Flow<Map<ChannelRef, Instant>> =
        cache
            .map { cache -> cache.entries.mapNotNull { (ref, entry) -> entry.time?.let { ref to it } }.toMap() }
            .distinctUntilChanged()

    /** Looks up [ref] in [scope] unless its answer is fresh, it is already loading, or its platform needs no lookup. */
    fun request(
        ref: ChannelRef,
        scope: CoroutineScope,
    ) {
        val platform = platforms.firstOrNull { it.id == ref.platform && it.capabilities.lastLiveLookup } ?: return
        val now = clock.now()
        var claimed = false
        cache.update { current ->
            val fresh = current.entries[ref]?.let { now - it.checkedAt < ttl } == true
            claimed = !fresh && ref !in current.loading
            if (claimed) current.copy(loading = current.loading + ref) else current
        }
        if (!claimed) return
        scope.launch {
            try {
                val lookup = permits.withPermit { withContext(dispatchers.io) { lookUp(platform, ref) } }
                cache.update { current ->
                    val time =
                        when (lookup) {
                            is Lookup.Answer -> lookup.time
                            Lookup.Failed -> current.entries[ref]?.time
                        }
                    current.copy(entries = current.entries + (ref to Entry(time, clock.now())))
                }
            } finally {
                cache.update { it.copy(loading = it.loading - ref) }
            }
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
    )

    private data class Cache(
        val entries: Map<ChannelRef, Entry> = emptyMap(),
        val loading: Set<ChannelRef> = emptySet(),
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

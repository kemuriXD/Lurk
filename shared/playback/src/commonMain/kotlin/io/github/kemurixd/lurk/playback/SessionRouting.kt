package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.getOrNull
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.proxy.ChainResult
import io.github.kemurixd.lurk.playback.proxy.Route
import io.github.kemurixd.lurk.playback.proxy.blamesProxy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds

/** Where one session's playlists go right now. [generation] goes up with every change. */
internal data class RouteState(
    val grant: PlaybackGrant,
    val route: Route,
    val status: ProxyStatus,
    val masterUrl: String,
    val generation: Int,
)

/** A playlist response and the route state it was fetched under. */
internal data class Routed(
    val state: RouteState,
    val outcome: Outcome<String>,
)

/**
 * The route state machine of one [PlaybackSession]: failover, the 403 re-resolve and the once-only ads switch.
 *
 * Every change of [RouteState] happens while holding [transitions]; plain fetches never take it. A fetch that fails
 * on a proxy takes the lock and walks the chain only if the route is still the one it used (same generation).
 * Anyone who failed on that route meanwhile waits for the lock, sees the new generation and retries there, so one
 * dead proxy causes one walk and one switch. The proxies probed during a walk are fetched under the lock on purpose:
 * that is what makes the walk single-flight. The 403 re-resolve and the ads switch are also single-flight under the
 * same lock, and their once-per-30-s and once-per-session checks are made inside it.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SessionRouting(
    private val channel: ChannelRef,
    private val source: PlaybackSource,
    private val settings: ProxySettings,
    private val router: StreamRouter,
    initial: Opening,
) {
    private val transitions = Mutex()

    /** Written only while holding [transitions]; read anywhere. */
    @Volatile
    var current = RouteState(initial.grant, initial.route, initial.status, initial.masterUrl, generation = 0)
        private set

    /** Guarded by [transitions]. */
    private var lastReResolve: ComparableTimeMark? = null

    private val adSwitchClaimed = AtomicBoolean(false)

    @Volatile
    private var closed = false

    private val mutableRoute = MutableStateFlow(initial.route)
    private val mutableStatus = MutableStateFlow(initial.status)
    private val mutableEvents =
        MutableSharedFlow<PlaybackEvent>(
            extraBufferCapacity = EVENT_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val route: StateFlow<Route> = mutableRoute.asStateFlow()
    val status: StateFlow<ProxyStatus> = mutableStatus.asStateFlow()
    val events: SharedFlow<PlaybackEvent> = mutableEvents.asSharedFlow()

    /** Fetches [url] on the current route, failing over when a proxy lets it down. */
    suspend fun fetch(url: String): Routed {
        var result = current.let { Routed(it, attempt(it, url)) }
        var rounds = 0
        while (failedOnProxy(result, url) && rounds++ < settings.endpoints.size + 1) {
            val seen = result.state
            result = transitions.withLock { if (current.generation == seen.generation) failOver(seen, url) else null }
                ?: current.let { Routed(it, attempt(it, url)) }
        }
        return result
    }

    /** After a playlist 403: a fresh grant and route, then a Reload. At most once per 30 s; failures change nothing. */
    suspend fun reResolve() =
        transitions.withLock {
            val last = lastReResolve
            if (closed || (last != null && last.elapsedNow() < RE_RESOLVE_INTERVAL)) return@withLock
            lastReResolve = router.timeSource.markNow()
            val grant = source.grant(channel).getOrNull() ?: return@withLock
            router
                .open(channel, grant, source.adPolicy, settings, forceProxy = adSwitchClaimed.load())
                .getOrNull()
                ?.let(::adopt)
        }

    /**
     * A stitched ad arrived over [used]. In [ProxyMode.WHEN_ADS_DETECTED] the first one on the unproxied direct route
     * moves the session onto the proxies for good, with a Reload. When no proxy works the session stays direct as
     * [ProxyStatus.Unavailable].
     */
    suspend fun onStitchedAd(used: RouteState) {
        val eligible =
            settings.mode == ProxyMode.WHEN_ADS_DETECTED &&
                settings.active &&
                used.route == Route.Direct &&
                used.status == ProxyStatus.NotUsed
        if (!eligible || !adSwitchClaimed.compareAndSet(false, true)) return
        transitions.withLock {
            val s = current
            if (closed || s.route != Route.Direct || s.status != ProxyStatus.NotUsed) return@withLock
            when (val result = router.proxiedMaster(channel, s.grant, settings.endpoints)) {
                is ChainResult.Success -> adopt(result.value)
                is ChainResult.AllFailed -> moveTo(Route.Direct, ProxyStatus.Unavailable)
            }
        }
    }

    fun close() {
        closed = true
    }

    private suspend fun attempt(
        s: RouteState,
        url: String,
    ): Outcome<String> {
        val endpoint = proxyFor(s, url)
        val headers = headersFor(s, url)
        return if (endpoint ==
            null
        ) {
            router.get(Route.Direct, url, headers)
        } else {
            router.throughProxy(endpoint, url, headers)
        }
    }

    /**
     * Holding [transitions], with the route still the one [seen] used: retries [url] on the other HTTP proxies and
     * moves the session to the first that answers. Returns null after moving the session direct, so the caller
     * retries there. Templates are not candidates: they rewrite the master only and cannot carry another URL.
     */
    private suspend fun failOver(
        seen: RouteState,
        url: String,
    ): Routed? {
        val failed = (seen.route as Route.ViaProxy).endpoint
        val headers = headersFor(seen, url)
        var reached: ProxyEndpoint? = null
        val result =
            router.chain.first(settings.endpoints.filterIsInstance<ProxyEndpoint.Http>(), skip = setOf(failed)) {
                reached = it
                router.get(Route.ViaProxy(it), url, headers)
            }
        // A stream error (a 403, say) came through a proxy that works: move there and let the caller see the error.
        val streamError = (result as? ChainResult.AllFailed)?.lastError?.takeUnless { it.blamesProxy() }
        val alive = reached
        return when {
            result is ChainResult.Success -> {
                val moved = moveTo(Route.ViaProxy(result.endpoint), ProxyStatus.Active(result.endpoint.label))
                Routed(moved, Outcome.Success(result.value))
            }

            streamError != null && alive != null -> {
                Routed(moveTo(Route.ViaProxy(alive), ProxyStatus.Active(alive.label)), Outcome.Failure(streamError))
            }

            else -> {
                moveTo(Route.Direct, ProxyStatus.Unavailable)
                null
            }
        }
    }

    /** Holding [transitions]: takes a newly opened master and tells the player to load it. */
    private fun adopt(opening: Opening) {
        commit(RouteState(opening.grant, opening.route, opening.status, opening.masterUrl, current.generation + 1))
        mutableEvents.tryEmit(PlaybackEvent.Reload(opening.masterUrl))
    }

    /** Holding [transitions]: moves the playlists of the same master to [route]. */
    private fun moveTo(
        route: Route,
        status: ProxyStatus,
    ): RouteState = commit(current.copy(route = route, status = status, generation = current.generation + 1))

    private fun commit(next: RouteState): RouteState {
        current = next
        mutableRoute.value = next.route
        mutableStatus.value = next.status
        return next
    }

    private companion object {
        val RE_RESOLVE_INTERVAL = 30.seconds
        const val EVENT_BUFFER = 8

        fun failedOnProxy(
            result: Routed,
            url: String,
        ): Boolean {
            val error = (result.outcome as? Outcome.Failure)?.error
            return error != null && error.blamesProxy() && proxyFor(result.state, url) != null
        }

        /** The proxy [url] travels through on [s], or null when it goes direct. A template proxies its master only. */
        fun proxyFor(
            s: RouteState,
            url: String,
        ): ProxyEndpoint? =
            when (val endpoint = (s.route as? Route.ViaProxy)?.endpoint) {
                is ProxyEndpoint.Http -> endpoint
                is ProxyEndpoint.UrlTemplate -> endpoint.takeIf { url == s.masterUrl }
                null -> null
            }

        fun headersFor(
            s: RouteState,
            url: String,
        ): Map<String, String> {
            val template = (s.route as? Route.ViaProxy)?.endpoint as? ProxyEndpoint.UrlTemplate
            return if (template != null && url == s.masterUrl) masterHeaders(template, s.grant) else s.grant.headers
        }
    }
}

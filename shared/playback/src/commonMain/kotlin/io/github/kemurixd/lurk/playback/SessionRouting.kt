package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.getOrNull
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.proxy.ChainResult
import io.github.kemurixd.lurk.playback.proxy.Route
import io.github.kemurixd.lurk.playback.proxy.blamesProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 *
 * The ads switch runs in [scope], not in the fetch that found the ad, so the player cancelling that load cannot lose
 * it. Nothing is committed once [close] has been called.
 *
 * A URL template's host is a third party: requests to it never carry the source's headers and never go through an
 * HTTP proxy.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SessionRouting(
    private val channel: ChannelRef,
    private val source: PlaybackSource,
    private val settings: ProxySettings,
    private val router: StreamRouter,
    private val scope: CoroutineScope,
    initial: Opening,
) {
    private val transitions = Mutex()
    private val templateHosts =
        settings.endpoints
            .filterIsInstance<ProxyEndpoint.UrlTemplate>()
            .map { it.label.lowercase() }
            .toSet()

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
        while (!closed && failedOnProxy(result, url, templateHosts) && rounds++ < settings.endpoints.size + 1) {
            val seen = result.state
            val handled =
                transitions.withLock {
                    when {
                        closed -> result
                        current.generation != seen.generation -> null
                        else -> failOver(seen, url)
                    }
                }
            result = handled ?: current.let { Routed(it, attempt(it, url)) }
        }
        return result
    }

    /** After a playlist 403: a fresh grant and route, then a Reload. At most once per 30 s; failures change nothing. */
    suspend fun reResolve() = transitions.withLock { renew() }

    /**
     * A stitched ad arrived over [used]. In [ProxyMode.WHEN_ADS_DETECTED] the first one on the unproxied direct route
     * moves the session onto the proxies for good, with a Reload. The switch runs in the session's scope, so it
     * completes even if the fetch that found the ad is cancelled.
     */
    fun onStitchedAd(used: RouteState) {
        val eligible =
            settings.mode == ProxyMode.WHEN_ADS_DETECTED &&
                settings.active &&
                used.route == Route.Direct &&
                used.status == ProxyStatus.NotUsed
        if (eligible && adSwitchClaimed.compareAndSet(false, true)) scope.launch { switchForAds() }
    }

    fun close() {
        closed = true
    }

    private suspend fun attempt(
        s: RouteState,
        url: String,
    ): Outcome<String> {
        val headers = headersFor(s, url, templateHosts)
        return when (val endpoint = proxyFor(s, url, templateHosts)) {
            null -> router.get(Route.Direct, url, headers)
            else -> router.throughProxy(endpoint, url, headers)
        }
    }

    /**
     * Holding [transitions], with the route still the one [seen] used. A failed template is replaced by opening the
     * master again. A failed HTTP proxy has [url] retried on the other HTTP proxies, and the session moves to the
     * first that answers; templates are not candidates there, as they cannot carry another URL. Returns null after
     * moving the session direct, so the caller retries there.
     */
    private suspend fun failOver(
        seen: RouteState,
        url: String,
    ): Routed? {
        val failed = (seen.route as Route.ViaProxy).endpoint
        if (failed is ProxyEndpoint.UrlTemplate) return reopen(seen, failed)
        val headers = headersFor(seen, url, templateHosts)
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
                val moved = seen.moved(Route.ViaProxy(result.endpoint), ProxyStatus.Active(result.endpoint.label))
                Routed(commit(moved, reload = false), Outcome.Success(result.value))
            }

            streamError != null && alive != null -> {
                val moved = seen.moved(Route.ViaProxy(alive), ProxyStatus.Active(alive.label))
                Routed(commit(moved, reload = false), Outcome.Failure(streamError))
            }

            else -> {
                commit(seen.moved(Route.Direct, ProxyStatus.Unavailable), reload = false)
                null
            }
        }
    }

    /**
     * Holding [transitions]: the template [failed] on its own master. The master is opened again over the other
     * proxies, or directly as [ProxyStatus.Unavailable], and the player is told to reload; the caller gets the new
     * master's text in place of the template's.
     */
    private suspend fun reopen(
        seen: RouteState,
        failed: ProxyEndpoint,
    ): Routed =
        when (val opened = router.viaProxies(channel, seen.grant, settings.endpoints - failed)) {
            is Outcome.Success -> {
                Routed(
                    commit(opened.value.after(seen), reload = true),
                    Outcome.Success(opened.value.masterText),
                )
            }

            is Outcome.Failure -> {
                Routed(seen, opened)
            }
        }

    /** Holding [transitions]: a fresh grant and route, then a Reload. At most once per 30 s. */
    private suspend fun renew() {
        val last = lastReResolve
        if (closed || (last != null && last.elapsedNow() < RE_RESOLVE_INTERVAL)) return
        lastReResolve = router.timeSource.markNow()
        val opening =
            source.grant(channel).getOrNull()?.let { grant ->
                router.open(channel, grant, source.adPolicy, settings, forceProxy = adSwitchClaimed.load()).getOrNull()
            }
        if (opening != null) commit(opening.after(current), reload = true)
    }

    /**
     * Runs once per session. Proxy errors leave the session direct but visibly so ([ProxyStatus.Unavailable]). A
     * stream error is most likely an expired grant, so a fresh one is taken through the re-resolve path (its 30 s
     * throttle included) and the proxies are tried again with it.
     */
    private suspend fun switchForAds() =
        transitions.withLock {
            val s = current
            if (closed || s.route != Route.Direct || s.status != ProxyStatus.NotUsed) return@withLock
            val result = router.proxiedMaster(channel, s.grant, settings.endpoints)
            val error = (result as? ChainResult.AllFailed)?.lastError
            when {
                result is ChainResult.Success -> commit(result.value.after(s), reload = true)
                error != null && !error.blamesProxy() -> renew()
                else -> commit(s.moved(Route.Direct, ProxyStatus.Unavailable), reload = false)
            }
        }

    /** Holding [transitions]: publishes [next], and a Reload of its master when [reload]. Inert once closed. */
    private fun commit(
        next: RouteState,
        reload: Boolean,
    ): RouteState {
        if (closed) return current
        current = next
        mutableRoute.value = next.route
        mutableStatus.value = next.status
        if (reload) mutableEvents.tryEmit(PlaybackEvent.Reload(next.masterUrl))
        return next
    }

    private companion object {
        val RE_RESOLVE_INTERVAL = 30.seconds
        const val EVENT_BUFFER = 8

        fun RouteState.moved(
            route: Route,
            status: ProxyStatus,
        ): RouteState = copy(route = route, status = status, generation = generation + 1)

        fun Opening.after(previous: RouteState): RouteState =
            RouteState(grant, route, status, masterUrl, previous.generation + 1)

        fun failedOnProxy(
            result: Routed,
            url: String,
            templateHosts: Set<String>,
        ): Boolean {
            val error = (result.outcome as? Outcome.Failure)?.error
            return error != null && error.blamesProxy() && proxyFor(result.state, url, templateHosts) != null
        }

        /**
         * The proxy [url] travels through on [s], or null when it goes direct. A template proxies its own master
         * only, and nothing on a template host goes through an HTTP proxy.
         */
        fun proxyFor(
            s: RouteState,
            url: String,
            templateHosts: Set<String>,
        ): ProxyEndpoint? =
            when (val endpoint = (s.route as? Route.ViaProxy)?.endpoint) {
                is ProxyEndpoint.Http -> endpoint.takeUnless { onTemplateHost(url, templateHosts) }
                is ProxyEndpoint.UrlTemplate -> endpoint.takeIf { url == s.masterUrl }
                null -> null
            }

        /** The source's headers, except for a template's host, which is a third party. */
        fun headersFor(
            s: RouteState,
            url: String,
            templateHosts: Set<String>,
        ): Map<String, String> = if (onTemplateHost(url, templateHosts)) emptyMap() else s.grant.headers

        fun onTemplateHost(
            url: String,
            templateHosts: Set<String>,
        ): Boolean = templateHosts.isNotEmpty() && hostOf(url).lowercase() in templateHosts
    }
}

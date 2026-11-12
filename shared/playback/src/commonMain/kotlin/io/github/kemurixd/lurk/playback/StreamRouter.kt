package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.ads.AdMarkers
import io.github.kemurixd.lurk.playback.proxy.ChainResult
import io.github.kemurixd.lurk.playback.proxy.ProxyChain
import io.github.kemurixd.lurk.playback.proxy.Route
import io.github.kemurixd.lurk.playback.proxy.RouteClients
import io.github.kemurixd.lurk.playback.proxy.blamesProxy
import io.github.kemurixd.lurk.playback.proxy.getText
import kotlin.time.TimeSource

/** A grant whose master playlist has been fetched over [route]. */
internal data class Opening(
    val grant: PlaybackGrant,
    val route: Route,
    val status: ProxyStatus,
    val masterUrl: String,
    val masterText: String,
)

/**
 * What a resolve and its session share: the route clients, the proxy chain, and how a grant becomes a master
 * playlist (step 2 of the routing). Nothing here keeps per-session state.
 */
internal class StreamRouter(
    private val routes: RouteClients,
    val chain: ProxyChain,
    val markers: () -> AdMarkers,
    val dispatchers: DispatcherProvider,
    val timeSource: TimeSource.WithComparableMarks,
) {
    /**
     * Picks the route for [grant] and fetches its master. With [forceProxy] the proxies are tried even in
     * [ProxyMode.WHEN_ADS_DETECTED], which a session uses once it has switched for ads. A proxy problem never fails
     * this: when no proxy works the master is fetched directly and the status is [ProxyStatus.Unavailable].
     */
    suspend fun open(
        channel: ChannelRef,
        grant: PlaybackGrant,
        policy: AdPolicy,
        settings: ProxySettings,
        forceProxy: Boolean = false,
    ): Outcome<Opening> =
        when {
            policy == AdPolicy.UNTOUCHED || !settings.active -> direct(grant, ProxyStatus.NotUsed)
            grant.tokenFlags?.adFree == true -> direct(grant, ProxyStatus.SkippedAdFree)
            settings.mode == ProxyMode.ALWAYS || forceProxy -> viaProxies(channel, grant, settings.endpoints)
            else -> direct(grant, ProxyStatus.NotUsed)
        }

    /** Fetches the master through the first proxy that works. A stream error ends the walk (see [ProxyChain]). */
    suspend fun proxiedMaster(
        channel: ChannelRef,
        grant: PlaybackGrant,
        endpoints: List<ProxyEndpoint>,
    ): ChainResult<Opening> =
        chain.first(endpoints) { endpoint ->
            val route = Route.ViaProxy(endpoint)
            val url = masterUrlFor(endpoint, channel, grant)
            get(route, url, masterHeaders(endpoint, grant)).map {
                Opening(grant, route, ProxyStatus.Active(endpoint.label), url, it)
            }
        }

    /**
     * One request through [endpoint], counted in the proxy's health like any chain attempt. A thrown exception is
     * [LurkError.ProxyFailed], so a misbehaving proxy cannot stop playback.
     */
    suspend fun throughProxy(
        endpoint: ProxyEndpoint,
        url: String,
        headers: Map<String, String>,
    ): Outcome<String> =
        when (val result = chain.first(listOf(endpoint)) { get(Route.ViaProxy(it), url, headers) }) {
            is ChainResult.Success -> Outcome.Success(result.value)
            is ChainResult.AllFailed -> Outcome.Failure(result.lastError ?: LurkError.ProxyFailed)
        }

    /** A plain GET over [route]'s client; see [getText]. */
    suspend fun get(
        route: Route,
        url: String,
        headers: Map<String, String>,
        toTemplateHost: Boolean = false,
    ): Outcome<String> = routes.getText(route, url, headers, toTemplateHost)

    /**
     * The master through the first of [endpoints] that works; when none does (or the list is empty), directly as
     * [ProxyStatus.Unavailable]. A stream error from a proxy is returned as it came.
     */
    suspend fun viaProxies(
        channel: ChannelRef,
        grant: PlaybackGrant,
        endpoints: List<ProxyEndpoint>,
    ): Outcome<Opening> {
        val result = proxiedMaster(channel, grant, endpoints)
        val error = (result as? ChainResult.AllFailed)?.lastError
        return when {
            result is ChainResult.Success -> Outcome.Success(result.value)
            error != null && !error.blamesProxy() -> Outcome.Failure(error)
            else -> direct(grant, ProxyStatus.Unavailable)
        }
    }

    private suspend fun direct(
        grant: PlaybackGrant,
        status: ProxyStatus,
    ): Outcome<Opening> =
        get(Route.Direct, grant.masterUrl, grant.headers).map {
            Opening(grant, Route.Direct, status, grant.masterUrl, it)
        }
}

/** The template service is a third party, so the source's headers are only sent to the source's own hosts. */
internal fun masterHeaders(
    endpoint: ProxyEndpoint,
    grant: PlaybackGrant,
): Map<String, String> = if (endpoint is ProxyEndpoint.UrlTemplate) emptyMap() else grant.headers

/** The master URL [endpoint] fetches: the expanded template, or the grant's own master for an HTTP proxy. */
internal fun masterUrlFor(
    endpoint: ProxyEndpoint,
    channel: ChannelRef,
    grant: PlaybackGrant,
): String =
    when (endpoint) {
        is ProxyEndpoint.UrlTemplate -> templateMasterUrl(endpoint, channel.login)
        is ProxyEndpoint.Http -> grant.masterUrl
    }

private val USHER_PARAMS =
    listOf(
        "allow_source" to "true",
        "allow_audio_only" to "true",
        "fast_bread" to "true",
        "include_unavailable" to "true",
    )

/**
 * The template expanded for [login], plus the usher parameters it does not already carry. Existing parameters keep
 * their values and order.
 */
internal fun templateMasterUrl(
    template: ProxyEndpoint.UrlTemplate,
    login: String,
): String {
    val expanded = template.expand(login)
    val base = expanded.substringBefore('#')
    val fragment = expanded.substring(base.length)
    val present =
        base
            .substringAfter('?', "")
            .split('&')
            .map { it.substringBefore('=') }
            .toSet()
    val missing = USHER_PARAMS.filter { (name, _) -> name !in present }
    if (missing.isEmpty()) return expanded
    val separator =
        when {
            '?' !in base -> "?"
            base.endsWith('?') || base.endsWith('&') -> ""
            else -> "&"
        }
    return base + separator + missing.joinToString("&") { (name, value) -> "$name=$value" } + fragment
}

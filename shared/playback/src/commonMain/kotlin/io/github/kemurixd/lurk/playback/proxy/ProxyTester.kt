package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.github.kemurixd.lurk.playback.PlaybackSource
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.ads.AdDetector
import io.github.kemurixd.lurk.playback.hls.MasterPlaylist
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import io.github.kemurixd.lurk.playback.masterHeaders
import io.github.kemurixd.lurk.playback.templateMasterUrl
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration
import kotlin.time.TimeSource

/** What a proxy test found. */
sealed interface ProxyTestResult {
    /** The stream came through clean. [mediaThroughProxy] is false for a URL template, which only serves the master. */
    data class Passed(
        val latency: Duration,
        val mediaThroughProxy: Boolean,
    ) : ProxyTestResult

    /** The proxy answered, but its media playlist still carried stitched ads. */
    data class AdsLeaked(
        val roll: RollType,
    ) : ProxyTestResult

    /** The proxy itself failed: offline, timed out, refused, blocked or answered 407/5xx. */
    data class Unreachable(
        val error: LurkError,
    ) : ProxyTestResult

    /** The stream failed for a reason that is not the proxy's (an offline channel, a bad playlist). */
    data class StreamFailed(
        val error: LurkError,
    ) : ProxyTestResult
}

/**
 * Checks one proxy against a live sample channel: it fetches the master and the lowest-bandwidth video playlist the
 * way a session would, and looks for stitched ads. The caller picks the sample channel.
 */
class ProxyTester(
    private val source: PlaybackSource,
    private val routes: RouteClients,
    private val detector: AdDetector,
    private val timeSource: TimeSource,
) {
    suspend fun test(
        endpoint: ProxyEndpoint,
        sample: ChannelRef,
    ): ProxyTestResult =
        when (val g = source.grant(sample)) {
            is Outcome.Success -> probe(endpoint, sample, g.value)
            is Outcome.Failure -> ProxyTestResult.StreamFailed(g.error)
        }

    private suspend fun probe(
        endpoint: ProxyEndpoint,
        sample: ChannelRef,
        grant: PlaybackGrant,
    ): ProxyTestResult {
        val route = Route.ViaProxy(endpoint)
        val masterUrl =
            when (endpoint) {
                is ProxyEndpoint.UrlTemplate -> templateMasterUrl(endpoint, sample.login)
                is ProxyEndpoint.Http -> grant.masterUrl
            }
        val start = timeSource.markNow()
        val master = get(route, masterUrl, masterHeaders(endpoint, grant))
        val latency = start.elapsedNow()
        return when (master) {
            is Outcome.Failure -> {
                failed(master.error)
            }

            is Outcome.Success -> {
                // A template only serves the master; its media comes straight from the CDN, without our headers.
                val throughProxy = endpoint is ProxyEndpoint.Http
                checkMedia(
                    master = master.value,
                    masterUrl = masterUrl,
                    route = if (throughProxy) route else Route.Direct,
                    headers = if (throughProxy) grant.headers else emptyMap(),
                    result = { ProxyTestResult.Passed(latency, mediaThroughProxy = throughProxy) },
                )
            }
        }
    }

    private suspend fun checkMedia(
        master: String,
        masterUrl: String,
        route: Route,
        headers: Map<String, String>,
        result: () -> ProxyTestResult,
    ): ProxyTestResult {
        val variant =
            MasterPlaylist
                .variants(master, masterUrl)
                .filter { !it.audioOnly }
                .minByOrNull { it.bandwidth }
        return when (val media = if (variant == null) null else get(route, variant.url, headers)) {
            null -> {
                ProxyTestResult.StreamFailed(LurkError.SchemaChanged)
            }

            is Outcome.Failure -> {
                failed(media.error)
            }

            is Outcome.Success -> {
                val stitched = MediaPlaylist.parse(media.value)?.let { detector.inspect(it).stitched }
                when {
                    stitched == null -> ProxyTestResult.StreamFailed(LurkError.SchemaChanged)
                    stitched.isNotEmpty() -> ProxyTestResult.AdsLeaked(stitched.first().roll)
                    else -> result()
                }
            }
        }
    }

    private fun failed(error: LurkError): ProxyTestResult =
        if (error.blamesProxy()) ProxyTestResult.Unreachable(error) else ProxyTestResult.StreamFailed(error)

    private suspend fun get(
        route: Route,
        url: String,
        headers: Map<String, String>,
    ): Outcome<String> =
        outcomeOf {
            routes
                .clientFor(route)
                .get(url) { headers.forEach { (name, value) -> header(name, value) } }
                .bodyAsText()
        }
}

package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.ads.AdMarkers
import io.github.kemurixd.lurk.playback.hls.MasterPlaylist
import io.github.kemurixd.lurk.playback.proxy.ProxyChain
import io.github.kemurixd.lurk.playback.proxy.RouteClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Turns a channel into a [ResolvedStream] and the [PlaybackSession] that serves its playlists.
 *
 * The grant is always fetched directly. The route is then picked once per grant:
 * - [AdPolicy.UNTOUCHED] (Kick), proxy off or an empty list: direct, [ProxyStatus.NotUsed].
 * - An ad-free viewer ([TokenFlags.adFree]): direct, [ProxyStatus.SkippedAdFree].
 * - [ProxyMode.ALWAYS]: the first proxy that returns the master, [ProxyStatus.Active]. When none does, the master is
 *   fetched directly and the status is [ProxyStatus.Unavailable]. A stream error from a proxy (a 404, say) fails the
 *   resolve without trying the others.
 * - [ProxyMode.WHEN_ADS_DETECTED]: direct and [ProxyStatus.NotUsed] until the session sees a stitched ad.
 *
 * A [ProxyEndpoint.UrlTemplate] replaces the master URL with the template's; an HTTP proxy carries the source's own.
 */
class PlaylistResolver(
    private val sources: List<PlaybackSource>,
    routes: RouteClients,
    chain: ProxyChain,
    markers: () -> AdMarkers,
    private val dispatchers: DispatcherProvider,
    timeSource: TimeSource.WithComparableMarks,
) {
    private val router = StreamRouter(routes, chain, markers, dispatchers, timeSource)

    /**
     * Returns [LurkError.NotFound] when no source serves `channel.platform`, and a grant failure as it came. The
     * session runs under a child of [scope]; [PlaybackSession.close] cancels that child, never [scope] itself.
     */
    suspend fun resolve(
        channel: ChannelRef,
        prefs: PlaybackPrefs,
        scope: CoroutineScope,
    ): Outcome<ResolvedStream> {
        val source =
            sources.firstOrNull { it.platform == channel.platform } ?: return Outcome.Failure(LurkError.NotFound)
        return when (val grant = source.grant(channel)) {
            is Outcome.Failure -> {
                grant
            }

            is Outcome.Success -> {
                when (val opening = router.open(channel, grant.value, source.adPolicy, prefs.proxy)) {
                    is Outcome.Failure -> opening
                    is Outcome.Success -> Outcome.Success(streamOf(channel, source, prefs.proxy, opening.value, scope))
                }
            }
        }
    }

    private suspend fun streamOf(
        channel: ChannelRef,
        source: PlaybackSource,
        settings: ProxySettings,
        opening: Opening,
        scope: CoroutineScope,
    ): ResolvedStream {
        val variants =
            withContext(dispatchers.default) { MasterPlaylist.variants(opening.masterText, opening.masterUrl) }
        return ResolvedStream(
            channel = channel,
            masterUrl = opening.masterUrl,
            variants = variants,
            headers = opening.grant.headers,
            lowLatencyCapable = opening.grant.lowLatencyCapable,
            session = PlaybackSession(channel, source, settings, router, scope, opening),
        )
    }
}

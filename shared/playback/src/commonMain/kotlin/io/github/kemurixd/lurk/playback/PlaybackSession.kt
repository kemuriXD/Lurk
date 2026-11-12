package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.playback.ads.AdDetector
import io.github.kemurixd.lurk.playback.ads.AdTracker
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import io.github.kemurixd.lurk.playback.proxy.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Instant

sealed interface PlaybackEvent {
    /** The player must re-prepare with [masterUrl]: the session moved to a new route or took a fresh grant. */
    data class Reload(
        val masterUrl: String,
    ) : PlaybackEvent
}

/** Fetches one playlist. */
interface PlaylistFetcher {
    suspend fun fetch(url: String): Outcome<String>
}

/**
 * Serves every playlist request of one stream and keeps its route, proxy status and ad state.
 *
 * **Contract for the player:** only playlist (`.m3u8`) URLs go through [fetch]; media segments always load straight
 * from the CDN and never touch this class. [fetch] may be called concurrently, for the master and each rendition.
 *
 * - Every playlist goes over the current [route], so a proxied session proxies the master and every media refresh. A
 *   template route only rewrites the master: its media playlists go direct.
 * - When a proxy fails a request, the same URL is retried on the next HTTP proxy and the session moves there. When
 *   none is left the session plays direct with [ProxyStatus.Unavailable]. A proxy problem never stops playback, and a
 *   session that should be proxied never goes direct without saying so.
 * - A playlist 403 takes a fresh grant, picks the route again and emits [PlaybackEvent.Reload], at most once per
 *   30 s. The 403 itself is returned.
 * - Twitch media playlists feed the ad tracker and [typicalSegment]. In [ProxyMode.WHEN_ADS_DETECTED] the first
 *   stitched ad seen on the direct route moves the session onto the proxies for good, with a Reload. Cues never do.
 * - [AdPolicy.UNTOUCHED] (Kick) playlists are passed through unread: no tracker, no parsing, no proxy.
 *
 * Route changes are serialised without locking ordinary fetches; see [SessionRouting]. Built by [PlaylistResolver]
 * only. The session keeps a child job in the caller's scope until [close], so call it when the stream ends. Cancel
 * collectors of [route], [proxyStatus] and [events] at or before [close]: a route change racing [close] can still
 * publish one last value.
 */
class PlaybackSession internal constructor(
    channel: ChannelRef,
    source: PlaybackSource,
    settings: ProxySettings,
    private val router: StreamRouter,
    parent: CoroutineScope,
    initial: Opening,
) : PlaylistFetcher,
    AutoCloseable {
    /** The session's own child of the caller's scope. A supervisor, so a failing tracker task stays in here. */
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job + router.dispatchers.default)
    private val routing = SessionRouting(channel, source, settings, router, scope, initial)
    private val mutableTypical = MutableStateFlow<Duration?>(null)

    private val tracker: AdTracker? =
        if (source.adPolicy == AdPolicy.TWITCH_DETECT_AND_PROXY) {
            AdTracker(AdDetector(router.markers), scope, router.timeSource) { routing.current.route is Route.ViaProxy }
        } else {
            null
        }

    val route: StateFlow<Route> = routing.route
    val adState: StateFlow<AdState> = tracker?.state ?: MutableStateFlow<AdState>(AdState.None).asStateFlow()
    val proxyStatus: StateFlow<ProxyStatus> = routing.status

    /** Not replayed: collect it for the whole session. */
    val events: SharedFlow<PlaybackEvent> = routing.events

    /** The median segment length of the last Twitch media playlist; null until one arrives, and always for Kick. */
    val typicalSegment: StateFlow<Duration?> = mutableTypical.asStateFlow()

    /** Fetches a playlist (never a segment) and returns its text unchanged. */
    override suspend fun fetch(url: String): Outcome<String> {
        val (used, outcome) = routing.fetch(url)
        when (outcome) {
            is Outcome.Success -> inspect(outcome.value, used)
            is Outcome.Failure -> if (outcome.error == FORBIDDEN) routing.reResolve()
        }
        return outcome
    }

    /** The program date-time on screen, so a player that starts inside an ad break counts it down correctly. */
    fun reportPlayhead(programDateTime: Instant) {
        tracker?.reportPlayhead(programDateTime)
    }

    /** Stops the ad tracker and cancels the session's coroutines. The caller's scope keeps running. */
    override fun close() {
        routing.close()
        tracker?.close()
        job.cancel()
    }

    /** Twitch only: parses a media playlist off the main thread and feeds the tracker. Masters are skipped. */
    private suspend fun inspect(
        text: String,
        used: RouteState,
    ) {
        val tracker = tracker ?: return
        val info =
            withContext(router.dispatchers.default) {
                MediaPlaylist.parse(text)?.let { playlist ->
                    playlist.typicalSegment?.let { mutableTypical.value = it }
                    tracker.onMediaPlaylist(playlist)
                }
            }
        if (info?.hasStitched == true) routing.onStitchedAd(used)
    }

    private companion object {
        val FORBIDDEN = LurkError.Http(403)
    }
}

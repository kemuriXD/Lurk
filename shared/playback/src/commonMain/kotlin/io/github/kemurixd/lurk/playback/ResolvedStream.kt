package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.model.ChannelRef
import kotlinx.coroutines.flow.StateFlow

/**
 * A channel resolved to a playable master playlist.
 *
 * [masterUrl] is the URL that was actually fetched: the proxy template's URL when a template route won, otherwise
 * the source's master. [variants] and [headers] are as of this first resolve: after a [PlaybackEvent.Reload] the
 * player reads the variants from the master it reloads, and the session sends the current grant's headers itself.
 * Every playlist request must go through [session]; segments never do.
 *
 * [toString] leaves out [headers] and the variants' URLs, and shows only [masterUrl]'s scheme and host: its query
 * carries the playback token, and on a template route its path is the user's template, which may hold a key.
 */
data class ResolvedStream(
    val channel: ChannelRef,
    val masterUrl: String,
    val variants: List<Variant>,
    val headers: Map<String, String>,
    val lowLatencyCapable: Boolean,
    val session: PlaybackSession,
) {
    val adState: StateFlow<AdState> get() = session.adState

    override fun toString(): String =
        "ResolvedStream(channel=$channel, masterUrl=${originOf(masterUrl)}, variants=${variants.size}, " +
            "lowLatencyCapable=$lowLatencyCapable)"
}

/** [url] without its query and fragment, for [toString]s: a playlist URL's query carries its token. */
internal fun withoutQuery(url: String): String = url.substringBefore('#').substringBefore('?')

/** [url]'s scheme and host only, for [toString]s that must not show a path, port, user info or query. */
internal fun originOf(url: String): String {
    val scheme = url.substringBefore("://", missingDelimiterValue = "")
    return if (scheme.isEmpty()) hostOf(url) else "$scheme://${hostOf(url)}"
}

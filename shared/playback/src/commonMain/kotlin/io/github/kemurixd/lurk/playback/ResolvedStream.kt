package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.model.ChannelRef
import kotlinx.coroutines.flow.StateFlow

/**
 * A channel resolved to a playable master playlist.
 *
 * [masterUrl] is the URL that was actually fetched: the proxy template's URL when a template route won, otherwise
 * the source's master. [headers] are the source's request headers for the first grant. Every playlist request must
 * go through [session]; segments never do.
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
}

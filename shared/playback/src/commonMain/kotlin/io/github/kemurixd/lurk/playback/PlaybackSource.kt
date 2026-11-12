package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform

/** How the player treats ads for a platform. */
enum class AdPolicy {
    /** Parse the playlist for ad markers and use the proxy when configured. */
    TWITCH_DETECT_AND_PROXY,

    /** Leave the stream alone: no proxy, no playlist parsing, no ad data kept. */
    UNTOUCHED,
}

/** What a source hands the player for one channel. */
data class PlaybackGrant(
    val masterUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val tokenFlags: TokenFlags? = null,
    val lowLatencyCapable: Boolean = false,
)

/**
 * Resolves a channel to a master playlist. Grants are always fetched directly, never through a proxy.
 * Twitch requests use `playerType=site` and `platform=web`. [PlaybackGrant.tokenFlags] come from the
 * signed-in token when there is one.
 */
interface PlaybackSource {
    val platform: Platform
    val adPolicy: AdPolicy

    suspend fun grant(channel: ChannelRef): Outcome<PlaybackGrant>
}

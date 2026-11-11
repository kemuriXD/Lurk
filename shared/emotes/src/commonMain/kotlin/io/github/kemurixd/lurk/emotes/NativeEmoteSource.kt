package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.Platform

/** One channel on one platform, as far as emote loading is concerned. */
data class EmoteChannel(
    val platform: Platform,
    val channelId: String,
)

/** The platform's own emotes (Twitch or Kick), supplied by the platform module. */
fun interface NativeEmoteSource {
    suspend fun load(channel: EmoteChannel): List<Emote>
}

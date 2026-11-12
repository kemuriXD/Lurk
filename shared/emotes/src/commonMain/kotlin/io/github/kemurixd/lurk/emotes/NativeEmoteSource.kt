package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.Platform

/** One channel on one platform, as far as emote loading is concerned. */
data class EmoteChannel(
    val platform: Platform,
    val channelId: String,
)

/**
 * The platform's own emotes (Twitch or Kick), supplied by the platform module.
 *
 * On failure, throw: a [io.github.kemurixd.lurk.core.common.LurkException] carries its error into the source's
 * [SourceResult], and anything else becomes `LurkError.Unexpected`. A channel with no emotes returns an empty list,
 * and a 404 for a channel should be mapped to an empty list too, as [EmoteApi] does. [EmoteLoader] guards every call:
 * on a failure it keeps any stale cached list and retries in the background.
 */
fun interface NativeEmoteSource {
    suspend fun load(channel: EmoteChannel): List<Emote>
}

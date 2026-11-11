package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope

/**
 * Everything the loader fetches for a channel. [NATIVE] stands for the platform's own emotes: its [provider] is a
 * placeholder, and the loader uses TWITCH or KICK depending on the channel.
 */
enum class EmoteSource(
    val provider: EmoteProvider,
    val scope: EmoteScope,
    val twitchOnly: Boolean,
) {
    SEVENTV_GLOBAL(EmoteProvider.SEVENTV, EmoteScope.GLOBAL, false),
    SEVENTV_CHANNEL(EmoteProvider.SEVENTV, EmoteScope.CHANNEL, false),
    BTTV_GLOBAL(EmoteProvider.BTTV, EmoteScope.GLOBAL, true),
    BTTV_CHANNEL(EmoteProvider.BTTV, EmoteScope.CHANNEL, true),
    FFZ_GLOBAL(EmoteProvider.FFZ, EmoteScope.GLOBAL, true),
    FFZ_CHANNEL(EmoteProvider.FFZ, EmoteScope.CHANNEL, true),
    NATIVE(EmoteProvider.TWITCH, EmoteScope.CHANNEL, false),
}

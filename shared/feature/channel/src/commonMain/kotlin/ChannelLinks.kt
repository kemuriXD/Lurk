package io.github.kemurixd.lurk.feature.channel

import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform

/** The channel's public page on its platform's website. Never an API URL. */
fun channelUrl(ref: ChannelRef): String =
    when (ref.platform) {
        Platform.TWITCH -> "https://www.twitch.tv/${ref.login}"
        Platform.KICK -> "https://kick.com/${ref.login}"
    }

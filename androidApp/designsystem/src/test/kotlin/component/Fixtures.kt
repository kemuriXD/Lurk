package io.github.kemurixd.lurk.designsystem.component

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream

internal fun testStream(platform: Platform = Platform.TWITCH) =
    Stream(
        channel = ChannelRef(platform, "streamer1"),
        channelId = "1000001",
        displayName = "Streamer One",
        title = "Speedrunning all night",
        category = Category(platform, "1", "Retro Racers", null),
        viewers = 12_400,
        startedAt = null,
        thumbnailUrl = null,
        avatarUrl = null,
    )

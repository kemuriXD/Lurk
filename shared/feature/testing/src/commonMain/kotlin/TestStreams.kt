package io.github.kemurixd.lurk.feature.testing

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import kotlin.time.Instant

/** A stand-in live stream. Kick followed and searched items have a blank [channelId], so tests can pass `""`. */
fun testStream(
    platform: Platform,
    login: String = "streamer1",
    viewers: Long = 100,
    displayName: String = "Streamer One",
    channelId: String = "1000001",
    title: String = "Test stream",
    category: Category? = null,
    startedAt: Instant? = null,
): Stream =
    Stream(
        channel = ChannelRef(platform, login),
        channelId = channelId,
        displayName = displayName,
        title = title,
        category = category,
        viewers = viewers,
        startedAt = startedAt,
        thumbnailUrl = null,
        avatarUrl = null,
    )

/** A stand-in channel profile. Its chat ID follows the platforms: the login on Twitch, the ID on Kick. */
fun testChannel(
    platform: Platform,
    login: String = "streamer1",
    id: String = "1000001",
    displayName: String = "Streamer One",
    live: Stream? = null,
    followers: Long? = null,
): Channel =
    Channel(
        ref = ChannelRef(platform, login),
        id = id,
        chatId = if (platform == Platform.TWITCH) login else id,
        displayName = displayName,
        avatarUrl = null,
        bannerUrl = null,
        bio = null,
        followers = followers,
        live = live,
        lastStream = null,
    )

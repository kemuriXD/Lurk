package io.github.kemurixd.lurk.platform.kick.channel

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.fixture
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class KickChannelJsonTest {
    private val categoryOne =
        Category(Platform.KICK, "1", "Category 1", "https://cdn.example/a/1.png", 1000)

    private val liveStream =
        Stream(
            channel = ChannelRef(Platform.KICK, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Stream title 1",
            category = Category(Platform.KICK, "1", "Category 1", null, 1000),
            viewers = 1000,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            thumbnailUrl = "https://cdn.example/a/1.png",
            avatarUrl = "https://cdn.example/a/1.png",
        )

    private fun channelOf(file: String) = decodeKickChannel(Json.parseToJsonElement(fixture(file)))

    private fun channelOfText(text: String) = decodeKickChannel(Json.parseToJsonElement(text))

    @Test
    fun toChannelMapsTheLiveFixture() {
        val expected =
            Channel(
                ref = ChannelRef(Platform.KICK, "streamer1"),
                id = "1000001",
                chatId = "2000001",
                displayName = "Streamer One",
                avatarUrl = "https://cdn.example/a/1.png",
                bannerUrl = "https://cdn.example/a/1.png",
                bio = "Bio 1",
                followers = 1000,
                live = liveStream,
                lastStream = null,
            )

        assertEquals(expected, channelOf("api/channel-live.json").toChannel())
    }

    @Test
    fun chatIdIsTheChatroomNotTheChannel() {
        val channel = checkNotNull(channelOf("api/channel-live.json").toChannel())

        assertEquals("1000001", channel.id)
        assertEquals("2000001", channel.chatId)
    }

    @Test
    fun toStreamMapsTheLiveFixture() {
        assertEquals(liveStream, channelOf("api/channel-live.json").toStream())
    }

    @Test
    fun offlineChannelHasLastStreamAndNoStream() {
        val json = channelOf("api/channel-offline.json")
        val channel = checkNotNull(json.toChannel())

        assertNull(json.toStream())
        assertNull(channel.live)
        assertEquals(LastStream(null, categoryOne, null), channel.lastStream)
    }

    @Test
    fun channelWithoutChatroomIsNull() {
        val json = channelOfText("""{"id":1,"slug":"streamer1"}""")

        assertNull(json.toChannel())
    }

    @Test
    fun finishedLivestreamIsNotAStream() {
        val text = fixture("api/channel-live.json").replace("\"is_live\": true", "\"is_live\": false")

        assertNull(channelOfText(text).toStream())
        assertNull(channelOfText(text).toChannel()?.live)
    }

    @Test
    fun categoryIdIsTheSubcategoryNotItsParent() {
        val text =
            """
            {"id":1,"slug":"streamer1","chatroom":{"id":2},"recent_categories":[{"id":4242,"category_id":1,"name":"Category 1"}]}
            """.trimIndent()
        val lastStream = checkNotNull(channelOfText(text).toChannel()?.lastStream)

        assertEquals("4242", lastStream.category?.id)
    }

    @Test
    fun followersReadAsStringOrNumber() {
        val asText = channelOfText("""{"id":1,"slug":"s","chatroom":{"id":2},"followers_count":"1000"}""")
        val asNumber = channelOfText("""{"id":1,"slug":"s","chatroom":{"id":2},"followers_count":1000}""")

        assertEquals(1000L, asText.toChannel()?.followers)
        assertEquals(1000L, asNumber.toChannel()?.followers)
    }

    @Test
    fun startedAtReadsKicksSpaceSeparatedUtcTime() {
        assertEquals(Instant.parse("2026-01-01T10:20:30Z"), startedAt("2026-01-01 10:20:30"))
    }

    @Test
    fun startedAtReadsIsoTimes() {
        assertEquals(Instant.parse("2026-01-01T10:20:30Z"), startedAt("2026-01-01T10:20:30Z"))
        assertEquals(Instant.parse("2026-01-01T10:20:30Z"), startedAt(" 2026-01-01T12:20:30+02:00 "))
    }

    @Test
    fun startedAtIsNullForJunk() {
        assertNull(startedAt("yesterday"))
        assertNull(startedAt(""))
        assertNull(startedAt(null))
    }
}

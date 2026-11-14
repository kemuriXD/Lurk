package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PreviewKeyTest {
    private val preview = "https://cdn.example/previews-ttv/live_user_streamer1-440x248.jpg"
    private val started = Instant.parse("2026-01-01T10:00:00Z")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private fun stream(
        startedAt: Instant? = started,
        thumbnailUrl: String? = preview,
    ) = Stream(
        channel = ChannelRef(Platform.TWITCH, "streamer1"),
        channelId = "1000001",
        displayName = "Streamer One",
        title = "",
        category = null,
        viewers = 1,
        startedAt = startedAt,
        thumbnailUrl = thumbnailUrl,
        avatarUrl = null,
    )

    @Test
    fun theSameStreamKeepsItsUrlWithinFiveMinutes() {
        assertEquals(
            stream().withPreviewKey(now).thumbnailUrl,
            stream().withPreviewKey(now + 4.minutes + 59.seconds).thumbnailUrl,
        )
    }

    @Test
    fun theUrlChangesEveryFiveMinutes() {
        assertNotEquals(
            stream().withPreviewKey(now).thumbnailUrl,
            stream().withPreviewKey(now + 5.minutes).thumbnailUrl,
        )
    }

    @Test
    fun aNewStreamBySameLoginGetsANewUrl() {
        val earlier = stream(startedAt = started).withPreviewKey(now)
        val later = stream(startedAt = started + 1.hours).withPreviewKey(now)
        assertNotEquals(earlier.thumbnailUrl, later.thumbnailUrl)
    }

    @Test
    fun theKeyIsAQueryOnTheSamePreview() {
        val url = checkNotNull(stream().withPreviewKey(now).thumbnailUrl)
        assertTrue(url.startsWith("$preview?t="), url)
        assertEquals("$preview?t=${started.epochSeconds}-${now.epochSeconds / 300}", url)
    }

    @Test
    fun noPreviewStaysNone() {
        assertNull(stream(thumbnailUrl = null).withPreviewKey(now).thumbnailUrl)
    }

    @Test
    fun noStartTimeStillRefreshes() {
        val first = stream(startedAt = null).withPreviewKey(now).thumbnailUrl
        val next = stream(startedAt = null).withPreviewKey(now + 5.minutes).thumbnailUrl
        assertNotEquals(first, next)
    }
}

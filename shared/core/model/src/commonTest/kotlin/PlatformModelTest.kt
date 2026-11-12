package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class PlatformModelTest {
    @Test
    fun accountToStringHidesIdentity() {
        val text = Account(Platform.TWITCH, "123", "someone", "Someone", null).toString()
        assertFalse(text.contains("123"))
        assertFalse(text.contains("someone"))
        assertEquals("Account(TWITCH)", text)
    }

    @Test
    fun streamRoundTrips() {
        val category = Category(Platform.KICK, "1", "Just Chatting", null, 10L)
        val stream =
            Stream(
                channel = ChannelRef.of(Platform.KICK, "streamer1"),
                channelId = "1000001",
                displayName = "Streamer One",
                title = "Stream title 1",
                category = category,
                viewers = 42L,
                startedAt = Instant.fromEpochSeconds(1_700_000_000),
                thumbnailUrl = null,
                avatarUrl = null,
            )
        val json = Json.encodeToString(Stream.serializer(), stream)
        assertEquals(stream, Json.decodeFromString(Stream.serializer(), json))
    }
}

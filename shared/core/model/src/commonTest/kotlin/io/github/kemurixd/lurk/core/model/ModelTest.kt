package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelTest {
    @Test
    fun platformFromIdResolvesKnownIds() {
        assertEquals(Platform.KICK, Platform.fromId("kick"))
        assertEquals(Platform.TWITCH, Platform.fromId("twitch"))
        assertNull(Platform.fromId("youtube"))
    }

    @Test
    fun channelRefOfTrimsAndLowercasesLogin() {
        assertEquals("kaicenat", ChannelRef.of(Platform.TWITCH, " KaiCenat ").login)
    }

    @Test
    fun channelRefRejectsWhitespaceInLogin() {
        assertFailsWith<IllegalArgumentException> { ChannelRef(Platform.TWITCH, "a b") }
    }

    @Test
    fun channelRefRejectsBlankLogin() {
        assertFailsWith<IllegalArgumentException> { ChannelRef(Platform.KICK, "") }
    }

    @Test
    fun pageSurvivesJsonRoundTrip() {
        val page = Page(listOf(ChannelRef.of(Platform.KICK, "x")), "c1")
        val json = Json.encodeToString(Page.serializer(ChannelRef.serializer()), page)
        assertEquals(page, Json.decodeFromString(Page.serializer(ChannelRef.serializer()), json))
    }
}

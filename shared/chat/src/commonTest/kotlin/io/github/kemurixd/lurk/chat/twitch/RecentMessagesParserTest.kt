package io.github.kemurixd.lurk.chat.twitch

import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.chat.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentMessagesParserTest {
    @Test
    fun fixtureGivesFiveHistoricalMessages() {
        val events = RecentMessagesParser.parse(fixture("twitch/recent-messages.json"))
        val messages = events.filterIsInstance<ChatEvent.Message>().map { it.message }
        assertEquals(5, events.size)
        assertEquals(5, messages.size)
        assertTrue(messages.all { it.historical })
        assertEquals("what is the current level ?", messages.first().text)
    }

    @Test
    fun badLinesAreSkipped() {
        val json =
            """
            {"messages": ["garbage", 42, null, "PING :tmi.twitch.tv",
              "@room-id=1;tmi-sent-ts=5 :tmi.twitch.tv CLEARCHAT #c",
              "@id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :hi"], "error": null}
            """.trimIndent()
        val events = RecentMessagesParser.parse(json)
        assertEquals(2, events.size)
        assertEquals(ChatEvent.ChatCleared, events[0])
        assertTrue((events[1] as ChatEvent.Message).message.historical)
    }

    @Test
    fun badBodyIsEmpty() {
        listOf("", "<html>", "[]", "{}", """{"messages": "nope"}""", "null").forEach {
            assertEquals(emptyList(), RecentMessagesParser.parse(it), "'$it'")
        }
    }
}

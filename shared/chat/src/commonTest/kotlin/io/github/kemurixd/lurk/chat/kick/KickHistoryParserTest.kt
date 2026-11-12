package io.github.kemurixd.lurk.chat.kick

import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.chat.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KickHistoryParserTest {
    @Test
    fun fixtureGivesFiveHistoricalMessagesOldestFirst() {
        val events = KickHistoryParser.parse(fixture("kick/history.json"))
        val messages = events.map { (it as ChatEvent.Message).message }
        assertEquals(5, messages.size)
        assertTrue(messages.all { it.historical })
        assertEquals(messages.map { it.sentAt }.sorted(), messages.map { it.sentAt })
        assertEquals("see you tomorrow", messages.first().text)
        assertEquals("good game everyone", messages.last().text)
        assertEquals("1000100", messages.first().channelId)
        assertEquals("1000252", messages.first().sender?.id)
        assertEquals(
            listOf("moderator", "vip", "og", "subscriber"),
            messages
                .first()
                .sender
                ?.badges
                ?.map { it.setId },
        )
        val withEmote = messages.single { it.text == "nice one chanWave" }
        assertEquals(
            "chanWave",
            withEmote.nativeEmotes
                .single()
                .emote.name,
        )
    }

    @Test
    fun badBodiesGiveNothing() {
        listOf("", "<html>", "[]", "{}", """{"data":{"messages":"x"}}""", "null").forEach {
            assertEquals(emptyList(), KickHistoryParser.parse(it), "'$it'")
        }
    }

    @Test
    fun badItemsAreSkipped() {
        val json =
            """
            {"data":{"messages":[1,"x",{"id":"a"},
              {"id":"b","chat_id":5,"content":"hi","created_at":"2026-11-07T17:55:07Z","sender":{"id":2,"username":"u"}}]}}
            """.trimIndent()
        assertEquals(1, KickHistoryParser.parse(json).size)
    }
}

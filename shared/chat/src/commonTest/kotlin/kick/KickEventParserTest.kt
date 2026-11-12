package io.github.kemurixd.lurk.chat.kick

import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.chat.fixture
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class KickEventParserTest {
    private val frames: List<String> =
        fixture("kick/frames.jsonl").lines().filter { it.isNotBlank() }.map {
            if (it.startsWith("\"")) Json.decodeFromString<String>(it) else it
        }

    private fun parseLine(line: Int): List<ChatEvent> = KickEventParser.parseFrame(frames[line - 1])

    private fun messageAt(line: Int): ChatMessage = assertIs<ChatEvent.Message>(parseLine(line).single()).message

    @Test
    fun fixtureHas28Lines() {
        assertEquals(28, frames.size)
    }

    @Test
    fun everyFixtureLineMapsAsListed() {
        val none = emptyList<String>()
        val chat = "Message:CHAT"
        val expected: Map<Int, List<String>> =
            mapOf(
                1 to none,
                2 to none,
                3 to none,
                4 to none,
                5 to listOf(chat),
                6 to listOf(chat),
                7 to listOf(chat),
                8 to listOf(chat),
                9 to listOf(chat),
                10 to listOf(chat),
                11 to listOf(chat),
                12 to listOf("Removed"),
                13 to listOf("UserCleared"),
                14 to listOf("UserCleared"),
                15 to listOf("Message:KICK_SUBSCRIPTION"),
                16 to listOf("Message:KICKS_GIFT"),
                17 to listOf("Message:KICK_HOST"),
                18 to listOf("Message:KICK_REWARD"),
                19 to none,
                20 to none,
                21 to listOf(chat, chat),
                22 to listOf("Message:KICK_GIFTED_SUBS"),
                23 to listOf("Message:KICK_LUCKY_GIFTEES"),
                24 to listOf("Pinned"),
                25 to listOf("Unpinned"),
                26 to listOf("Message:KICK_HOST"),
                27 to listOf("ChatCleared"),
                28 to none,
            )
        var total = 0
        for ((line, want) in expected) {
            val got = parseLine(line).map(::describe)
            assertEquals(want, got, "line $line")
            total += got.size
        }
        assertEquals(22, total)
    }

    private fun describe(event: ChatEvent): String =
        when (event) {
            is ChatEvent.Message -> "Message:${event.message.kind}"
            is ChatEvent.Pinned -> "Pinned"
            is ChatEvent.Unpinned -> "Unpinned"
            is ChatEvent.Removed -> "Removed"
            is ChatEvent.UserCleared -> "UserCleared"
            ChatEvent.ChatCleared -> "ChatCleared"
            else -> event.toString()
        }

    @Test
    fun plainMessageFields() {
        val m = messageAt(5)
        assertEquals("10000000-0000-4000-8000-000000000001", m.id)
        assertEquals(Platform.KICK, m.platform)
        assertEquals("1000100", m.channelId)
        assertEquals("good game everyone", m.text)
        assertEquals(Instant.parse("2026-11-07T18:00:01+00:00"), m.sentAt)
        val sender = assertNotNull(m.sender)
        assertEquals("1000201", sender.id)
        assertEquals("user_0201", sender.login)
        assertEquals("user_0201", sender.displayName)
        assertEquals("#DEB2FF", sender.color)
        assertEquals(listOf(Badge("subscriber", "3", "Subscriber")), sender.badges)
        assertNull(m.reply)
        assertTrue(!m.historical)
    }

    @Test
    fun emoteMessagesCarrySpans() {
        val m = messageAt(8)
        assertEquals("nice play chanHype chanWave see you tomorrow", m.text)
        assertEquals(listOf(10 to 18, 19 to 27), m.nativeEmotes.map { it.start to it.end })
        assertEquals(listOf("1000001", "1000002"), m.nativeEmotes.map { it.emote.id })
        assertEquals("chanHype", messageAt(7).text)
    }

    @Test
    fun replyFillsReplyRef() {
        val reply = assertNotNull(messageAt(10).reply)
        assertEquals("10000000-0000-4000-8000-000000000001", reply.parentId)
        assertEquals("user_0201", reply.parentLogin)
        assertEquals("good game everyone", reply.parentText)
    }

    @Test
    fun newShapeMessageParses() {
        // Frames 5-10 carry badges_v2 and message_ref next to the older fields.
        val plain = messageAt(5)
        assertEquals(MessageKind.CHAT, plain.kind)
        assertEquals(listOf(Badge("subscriber", "3", "Subscriber")), plain.sender?.badges)
        assertEquals(emptyList(), messageAt(6).sender?.badges)
        assertEquals("10000000-0000-4000-8000-000000000001", messageAt(10).reply?.parentId)
        val inner =
            """{\"id\":\"m1\",\"chatroom_id\":7,\"content\":\"yo\",\"type\":\"message\",""" +
                """\"created_at\":\"2026-11-07T18:00:01+00:00\",\"sender\":{\"id\":1,\"username\":\"u\",""" +
                """\"slug\":\"u\",\"identity\":{\"color\":\"#fff\",\"badges\":[],\"badges_v2\":""" +
                """[{\"name\":\"level\",\"badge_type\":\"global\",\"image_url\":\"x\"}]}},""" +
                """\"metadata\":{\"message_ref\":\"123\"}}"""
        val m = assertIs<ChatEvent.Message>(KickEventParser.parseFrame(chatFrame(inner)).single()).message
        assertEquals("yo", m.text)
        assertEquals(emptyList(), m.sender?.badges)
    }

    private fun chatFrame(innerEscaped: String): String =
        """{"push":{"channel":"chatrooms.7.v2","pub":{"data":{"event":"App\\Events\\ChatMessageEvent",""" +
            """"data":"$innerEscaped"}}}}"""

    @Test
    fun moderationEvents() {
        assertEquals(ChatEvent.Removed("10000000-0000-4000-8000-000000000002"), parseLine(12).single())
        assertEquals(ChatEvent.UserCleared("1000202"), parseLine(13).single())
        assertEquals(ChatEvent.UserCleared("1000203"), parseLine(14).single())
        assertEquals(ChatEvent.ChatCleared, parseLine(27).single())
    }

    @Test
    fun kicksGiftedHasGiftTags() {
        val m = messageAt(16)
        assertEquals("Hell Yeah", m.tags["gift_name"])
        assertEquals("1", m.tags["gift_amount"])
        assertEquals("10000000-0000-4000-8000-000000000013", m.id)
        assertEquals("user_0208", m.sender?.login)
        assertEquals("#FFFFFF", m.sender?.color)
        assertEquals("", m.text)
    }

    @Test
    fun noticeEventsCarryPlatformFieldsAsTags() {
        val sub = messageAt(15)
        assertEquals("6", sub.tags["months"])
        assertEquals("user_0204", sub.sender?.login)
        assertEquals("1000100", sub.channelId)
        assertTrue(sub.id.startsWith("kick:SubscriptionEvent:"))
        val gifted = messageAt(22)
        assertEquals("user_0231,user_0232", gifted.tags["gifted_usernames"])
        assertEquals("user_0230", gifted.sender?.login)
        val lucky = messageAt(23)
        assertEquals("user_0231,user_0232", lucky.tags["usernames"])
        assertEquals("user_0230", lucky.sender?.login)
        val host = messageAt(17)
        assertEquals("1713", host.tags["number_viewers"])
        assertEquals("user_0209", host.sender?.login)
        assertEquals("user_0301", messageAt(26).sender?.login)
        val reward = messageAt(18)
        assertEquals("Pick the next song", reward.tags["reward_title"])
        assertEquals("", reward.text)
        assertEquals("user_0210", reward.sender?.login)
        assertEquals("1000100", reward.channelId)
    }

    @Test
    fun pinnedEvents() {
        val pinned = assertIs<ChatEvent.Pinned>(parseLine(24).single())
        assertEquals("pinned text", pinned.message.text)
        assertEquals("1000100", pinned.message.channelId)
        assertEquals(ChatEvent.Unpinned(null), parseLine(25).single())
    }

    @Test
    fun malformedInnerDataSkipped() {
        assertEquals(emptyList(), parseLine(28))
    }

    @Test
    fun batchedFrameYieldsAll() {
        val events = parseLine(21)
        assertEquals(2, events.size)
        assertEquals(listOf("first of two", "second of two"), events.map { (it as ChatEvent.Message).message.text })
    }

    @Test
    fun oneBadReplyDoesNotSpoilTheRest() {
        val frame = "garbage\n" + frames[4] + "\n" + frames[27] + "\n" + frames[5]
        assertEquals(2, KickEventParser.parseFrame(frame).size)
    }

    @Test
    fun ignoredEventsAndControlFramesGiveNothing() {
        listOf(1, 2, 3, 4, 19, 20).forEach { assertEquals(emptyList(), parseLine(it), "line $it") }
        listOf("", "  ", "not json", "[]", "null", """{"push":1}""", """{"push":{"pub":{"data":{}}}}""").forEach {
            assertEquals(emptyList(), KickEventParser.parseFrame(it), "'$it'")
        }
    }

    @Test
    fun createdAtAcceptsEpochSecondsAndFallsBackToEpoch() {
        fun at(value: String): Instant {
            val inner =
                """{\"id\":\"m\",\"chatroom_id\":7,\"content\":\"x\",\"created_at\":$value,""" +
                    """\"sender\":{\"id\":1,\"username\":\"u\"}}"""
            return assertIs<ChatEvent.Message>(KickEventParser.parseFrame(chatFrame(inner)).single()).message.sentAt
        }
        assertEquals(Instant.fromEpochSeconds(1794074514), at("1794074514"))
        assertEquals(Instant.fromEpochMilliseconds(0), at("""\"soon\""""))
        assertEquals(Instant.fromEpochMilliseconds(0), at("null"))
    }

    private fun subFrame(
        user: String,
        createdAt: String = "2026-11-07T18:00:01+00:00",
    ): String =
        """{"push":{"channel":"chatrooms.7.v2","pub":{"data":{"event":"App\\Events\\SubscriptionEvent",""" +
            """"data":"{\"username\":\"$user\",\"months\":1,\"created_at\":\"$createdAt\"}"}}}}"""

    private fun idOf(frame: String): String =
        assertIs<ChatEvent.Message>(KickEventParser.parseFrame(frame).single()).message.id

    @Test
    fun syntheticIdsDifferForDifferentPayloadsInTheSameSecond() {
        assertTrue(idOf(subFrame("aaa")) != idOf(subFrame("bbb")))
        assertEquals(idOf(subFrame("aaa")), idOf(subFrame("aaa")))
    }

    @Test
    fun loginIsLowercaseAndDisplayNameKeepsCase() {
        val inner =
            """{\"id\":\"m\",\"chatroom_id\":7,\"content\":\"x\",\"sender\":{\"id\":1,\"username\":\"MixedCase\"}}"""
        val sender = assertIs<ChatEvent.Message>(KickEventParser.parseFrame(chatFrame(inner)).single()).message.sender
        assertEquals("mixedcase", sender?.login)
        assertEquals("MixedCase", sender?.displayName)
        val notice = assertIs<ChatEvent.Message>(KickEventParser.parseFrame(subFrame("MixedCase")).single()).message
        assertEquals("mixedcase", notice.sender?.login)
        assertEquals("MixedCase", notice.sender?.displayName)
    }

    @Test
    fun crlfSeparatedRepliesBothParse() {
        assertEquals(2, KickEventParser.parseFrame(frames[4] + "\r\n" + frames[5]).size)
    }

    @Test
    fun innerDataThatIsAnObjectIsAccepted() {
        val frame =
            """{"push":{"channel":"chatrooms.7.v2","pub":{"data":{"event":"App\\Events\\ChatroomClearEvent",""" +
                """"data":{"id":7}}}}}"""
        assertEquals(listOf<ChatEvent>(ChatEvent.ChatCleared), KickEventParser.parseFrame(frame))
        val chat =
            """{"push":{"channel":"chatrooms.7.v2","pub":{"data":{"event":"App\\Events\\ChatMessageEvent",""" +
                """"data":{"id":"m","chatroom_id":7,"content":"hey","sender":{"id":1,"username":"u"}}}}}}"""
        assertEquals("hey", assertIs<ChatEvent.Message>(KickEventParser.parseFrame(chat).single()).message.text)
    }
}

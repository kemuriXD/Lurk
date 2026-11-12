package io.github.kemurixd.lurk.chat.twitch

import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.ReplyRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TwitchEventMapperTest {
    private fun message(number: Int): ChatMessage =
        assertIs<ChatEvent.Message>(TwitchEventMapper.map(ircMessage(number)), "line $number").message

    private fun messageFrom(line: String): ChatMessage =
        assertIs<ChatEvent.Message>(TwitchEventMapper.map(requireNotNull(IrcParser.parse(line)))).message

    @Test
    fun plainPrivmsg() {
        val msg = message(5)
        assertEquals("10000000-0000-4000-8000-00000000a3eb", msg.id)
        assertEquals(Platform.TWITCH, msg.platform)
        assertEquals("1004839", msg.channelId)
        assertEquals("1004840", msg.sender?.id)
        assertEquals("user_5701", msg.sender?.login)
        assertEquals("user_5701", msg.sender?.displayName)
        assertEquals("#0000FF", msg.sender?.color)
        assertEquals("good game everyone", msg.text)
        assertEquals(Instant.fromEpochMilliseconds(1794074404521), msg.sentAt)
        assertEquals(MessageKind.CHAT, msg.kind)
        assertEquals(emptyList(), msg.nativeEmotes)
        assertFalse(msg.action)
        assertFalse(msg.firstMessage)
        assertFalse(msg.historical)
        assertEquals(0, msg.bits)
        assertNull(msg.reply)
    }

    @Test
    fun badgesAndEmptyColor() {
        assertEquals(listOf(Badge("subscriber", "0"), Badge("sub-gifter", "1")), message(6).sender?.badges)
        assertNull(message(17).sender?.color)
    }

    @Test
    fun badgeSetIdsAreNotValidated() {
        val msg = messageFrom("@badges=campaign-7-abc-def-sub/1,odd;id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :hi")
        assertEquals(listOf(Badge("campaign-7-abc-def-sub", "1")), msg.sender?.badges)
    }

    @Test
    fun displayNameFallsBackToLogin() {
        val msg = messageFrom("@display-name=;id=x;room-id=1;user-id=2 :someone!someone@someone PRIVMSG #c :hi")
        assertEquals("someone", msg.sender?.displayName)
    }

    @Test
    fun emotePositionsAreCodePoints() {
        val msg = message(13)
        assertEquals(listOf(5, 21), msg.nativeEmotes.map { it.start })
        msg.nativeEmotes.forEach { assertEquals("LUL", msg.text.substring(it.start, it.end)) }
        assertEquals(
            "LUL",
            msg.nativeEmotes
                .first()
                .emote.name,
        )
    }

    @Test
    fun nativeEmoteHasStaticCdnImages() {
        val emote = message(7).nativeEmotes.single().emote
        assertEquals("245", emote.id)
        assertEquals("ResidentSleeper", emote.name)
        assertEquals(EmoteProvider.TWITCH, emote.provider)
        assertEquals(EmoteScope.GLOBAL, emote.scope)
        assertEquals(
            listOf(
                EmoteImage(1, "https://static-cdn.jtvnw.net/emoticons/v2/245/default/dark/1.0"),
                EmoteImage(2, "https://static-cdn.jtvnw.net/emoticons/v2/245/default/dark/2.0"),
                EmoteImage(4, "https://static-cdn.jtvnw.net/emoticons/v2/245/default/dark/3.0"),
            ),
            emote.images,
        )
    }

    @Test
    fun repeatedRangesGiveOneSpanEach() {
        val spans = message(9).nativeEmotes
        assertEquals(3, spans.size)
        assertTrue(spans.all { it.emote.id == "425618" })
        assertEquals(listOf(0 to 3, 4 to 7, 8 to 11), spans.map { it.start to it.end })
    }

    @Test
    fun twoEmoteIdsAreSortedByPosition() {
        val msg = message(11)
        assertEquals(
            listOf("LUL", "TehePelo", "LUL", "TehePelo", "LUL", "TehePelo"),
            msg.nativeEmotes.map { it.emote.name },
        )
        msg.nativeEmotes.forEach { assertEquals(it.emote.name, msg.text.substring(it.start, it.end)) }
    }

    @Test
    fun outOfRangeEmoteSkipped() {
        val msg = message(43)
        assertEquals("short LUL", msg.text)
        assertEquals(listOf(6 to 9), msg.nativeEmotes.map { it.start to it.end })
    }

    @Test
    fun overlappingEmoteSkipped() {
        val msg = messageFrom("@emotes=1:0-2/2:2-4,6-8;id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :abcde fgh")
        assertEquals(listOf("1" to 0, "2" to 6), msg.nativeEmotes.map { it.emote.id to it.start })
    }

    @Test
    fun malformedEmoteTagIsIgnored() {
        val msg = messageFrom("@emotes=1:a-b,3-1,/:0-1/2;id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :abcde")
        assertEquals(emptyList(), msg.nativeEmotes)
    }

    @Test
    fun actionIsStripped() {
        val msg = message(14)
        assertTrue(msg.action)
        assertEquals("waves at chat", msg.text)
        assertEquals(listOf(Badge("moderator", "1"), Badge("bot-badge", "1")), msg.sender?.badges)
    }

    @Test
    fun actionEmotePositionsAreRelativeToVisibleText() {
        val msg =
            messageFrom("@emotes=25:6-10;id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :\u0001ACTION waves Kappa\u0001")
        assertEquals("waves Kappa", msg.text)
        assertEquals("Kappa", msg.text.substring(msg.nativeEmotes.single().start, msg.nativeEmotes.single().end))
    }

    @Test
    fun replyFillsReplyRef() {
        assertEquals(
            ReplyRef("10000000-0000-4000-8000-00000000a430", "user_5763", "what song is this"),
            message(15).reply,
        )
    }

    @Test
    fun bitsAndFirstMessage() {
        assertEquals(100, message(17).bits)
        assertTrue(message(18).firstMessage)
    }

    @Test
    fun keepsOnlyRendererTags() {
        val tags = message(42).tags
        assertEquals(mapOf("msg-id" to "animated-message", "animation-id" to "rainbow-eclipse"), tags)
        assertEquals(mapOf("msg-id" to "highlighted-message"), message(19).tags)
    }

    @Test
    fun badTimestampFallsBackToEpoch() {
        val msg = messageFrom("@tmi-sent-ts=soon;id=x;room-id=1;user-id=2 :u!u@u PRIVMSG #c :hi")
        assertEquals(Instant.fromEpochMilliseconds(0), msg.sentAt)
    }

    @Test
    fun resubUserNotice() {
        val msg = message(27)
        assertEquals(MessageKind.TWITCH_USER_NOTICE, msg.kind)
        assertEquals("still here, thanks for the streams", msg.text)
        assertEquals("user_5954 subscribed with Prime. They've subscribed for 8 months!", msg.systemText)
        assertEquals("resub", msg.tags["msg-id"])
        assertEquals("8", msg.tags["msg-param-cumulative-months"])
        assertEquals("Channel Subscription (user_5700)", msg.tags["msg-param-sub-plan-name"])
        assertNull(msg.tags["login"])
        assertEquals("user_5954", msg.sender?.login)
        assertEquals("1005090", msg.sender?.id)
    }

    @Test
    fun userNoticeWithoutMessageHasEmptyText() {
        val msg = message(26)
        assertEquals("", msg.text)
        assertEquals("resub", msg.tags["msg-id"])
    }

    @Test
    fun announcementKeepsColour() {
        val msg = message(30)
        assertEquals("PRIMARY", msg.tags["msg-param-color"])
        assertEquals("Welcome to the stream, the schedule is pinned below", msg.text)
        assertNull(msg.systemText)
    }

    @Test
    fun clearChatClearMsgRoomStateReconnect() {
        assertEquals(ChatEvent.UserCleared("1004840"), TwitchEventMapper.map(ircMessage(21)))
        assertEquals(ChatEvent.UserCleared("1004840"), TwitchEventMapper.map(ircMessage(22)))
        assertEquals(ChatEvent.ChatCleared, TwitchEventMapper.map(ircMessage(23)))
        assertEquals(ChatEvent.Removed("10000000-0000-4000-8000-00000000a3eb"), TwitchEventMapper.map(ircMessage(24)))
        assertEquals(
            ChatEvent.RoomState(mapOf("followers-only" to "15", "room-id" to "1004839")),
            TwitchEventMapper.map(ircMessage(37)),
        )
        assertEquals(ChatEvent.ReconnectRequested, TwitchEventMapper.map(ircMessage(39)))
    }

    @Test
    fun otherCommandsMapToNull() {
        listOf(1, 3, 4, 35, 38).forEach { assertNull(TwitchEventMapper.map(ircMessage(it)), "line $it") }
    }

    @Test
    fun wholeFixtureNeverThrows() {
        assertEquals(43, chatIrcLines.size)
        val events = chatIrcLines.map { line -> IrcParser.parse(line)?.let(TwitchEventMapper::map) }
        val messages = events.filterIsInstance<ChatEvent.Message>().map { it.message }
        assertEquals(30, messages.size)
        assertEquals(20, messages.count { it.kind == MessageKind.CHAT })
        assertEquals(10, messages.count { it.kind == MessageKind.TWITCH_USER_NOTICE })
        assertEquals(2, events.count { it is ChatEvent.UserCleared })
        assertEquals(1, events.count { it is ChatEvent.ChatCleared })
        assertEquals(1, events.count { it is ChatEvent.Removed })
        assertEquals(3, events.count { it is ChatEvent.RoomState })
        assertEquals(1, events.count { it is ChatEvent.ReconnectRequested })
        assertEquals(5, events.count { it == null })
    }

    @Test
    fun oddLinesNeverThrow() {
        val lines =
            listOf(
                "@ :u PRIVMSG",
                "@id=x :u PRIVMSG #c",
                "@=;;=;a :u!u@u PRIVMSG #c :",
                "@emotes=1:0-99999999999;id=x;room-id=1 :u!u@u PRIVMSG #c :\uD83D",
                "@id=x;room-id=1 :u!u@u PRIVMSG #c :\u0001ACTION ",
                "@id=x;room-id=1 :u!u@u PRIVMSG #c :\u0001ACTION\u0001",
                ":tmi.twitch.tv CLEARMSG #c",
                "@target-user-id=1 :tmi.twitch.tv CLEARCHAT",
                "USERNOTICE",
            )
        lines.forEach { line -> IrcParser.parse(line)?.let(TwitchEventMapper::map) }
    }
}

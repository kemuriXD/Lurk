package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.chat.kick.KickEventParser
import io.github.kemurixd.lurk.chat.twitch.TwitchEventMapper
import io.github.kemurixd.lurk.chat.twitch.ircMessage
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.ChatUser
import io.github.kemurixd.lurk.core.model.HighlightType
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class HighlightClassifierTest {
    private val kickFrames: List<String> =
        fixture("kick/frames.jsonl").lines().filter { it.isNotBlank() }.map {
            if (it.startsWith("\"")) Json.decodeFromString<String>(it) else it
        }

    private fun twitch(line: Int): ChatMessage =
        assertIs<ChatEvent.Message>(TwitchEventMapper.map(ircMessage(line)), "line $line").message

    private fun kick(line: Int): ChatMessage =
        assertIs<ChatEvent.Message>(KickEventParser.parseFrame(kickFrames[line - 1]).single(), "line $line").message

    private fun hand(
        text: String = "hello",
        login: String? = "someone",
        kind: MessageKind = MessageKind.CHAT,
        tags: Map<String, String> = emptyMap(),
        firstMessage: Boolean = false,
        bits: Int = 0,
    ) = ChatMessage(
        id = "m1",
        platform = Platform.TWITCH,
        channelId = "c1",
        sender = login?.let { ChatUser(id = "u1", login = it, displayName = it) },
        text = text,
        sentAt = Instant.fromEpochMilliseconds(0),
        kind = kind,
        bits = bits,
        firstMessage = firstMessage,
        tags = tags,
    )

    private fun parts(message: ChatMessage): List<MessagePart> = listOf(MessagePart.Text(message.text))

    private fun classify(
        message: ChatMessage,
        enabled: Set<HighlightType> = HighlightType.DEFAULTS,
        selfLogin: String? = null,
        parts: List<MessagePart> = parts(message),
    ) = HighlightClassifier.classify(message, parts, enabled, selfLogin)

    @Test
    fun subKindsAreTwitchSub() {
        for (line in listOf(25, 26, 27, 28, 29)) {
            assertEquals(Highlight(HighlightType.TWITCH_SUB), classify(twitch(line)), "line $line")
        }
    }

    @Test
    fun announcementCarriesColor() {
        assertEquals(Highlight(HighlightType.TWITCH_ANNOUNCEMENT, "PRIMARY"), classify(twitch(30)))
        assertEquals(Highlight(HighlightType.TWITCH_ANNOUNCEMENT, "GREEN"), classify(twitch(31)))
    }

    @Test
    fun cheerIsTwitchCheer() {
        assertEquals(Highlight(HighlightType.TWITCH_CHEER), classify(twitch(17)))
    }

    @Test
    fun kickEventsMapToTheirTypes() {
        assertEquals(Highlight(HighlightType.KICK_SUB), classify(kick(15)))
        assertEquals(Highlight(HighlightType.KICKS_GIFT), classify(kick(16)))
        assertEquals(Highlight(HighlightType.KICK_HOST), classify(kick(17)))
        assertEquals(Highlight(HighlightType.KICK_SUB), classify(kick(22)))
        assertEquals(Highlight(HighlightType.KICK_SUB), classify(kick(23)))
        assertNull(classify(kick(18)))
        assertEquals(
            Highlight(HighlightType.KICK_REWARD),
            classify(kick(18), enabled = setOf(HighlightType.KICK_REWARD)),
        )
    }

    @Test
    fun offByDefaultRowsNeedOptIn() {
        val cases =
            mapOf(
                HighlightType.CHANNEL_POINTS_HIGHLIGHT to twitch(19),
                HighlightType.SUB_ONLY_MODE to twitch(40),
                HighlightType.GIGANTIFIED_EMOTE to twitch(41),
                HighlightType.MESSAGE_EFFECT to twitch(42),
                HighlightType.FIRST_MESSAGE to twitch(18),
            )
        for ((type, message) in cases) {
            assertNull(classify(message), "$type with defaults")
            assertEquals(Highlight(type), classify(message, enabled = setOf(type)), "$type enabled")
        }
    }

    @Test
    fun disabledTypeIsSkippedInFavourOfLaterMatch() {
        val resubWithMention =
            hand(
                text = "hi @me",
                kind = MessageKind.TWITCH_USER_NOTICE,
                tags =
                    mapOf("msg-id" to "resub"),
            )
        val enabled = HighlightType.DEFAULTS - HighlightType.TWITCH_SUB
        assertEquals(
            Highlight(HighlightType.MENTION),
            classify(resubWithMention, enabled, "me", listOf(MessagePart.Mention("me", "@me"))),
        )
    }

    @Test
    fun subBeatsMention() {
        val msg = hand(text = "thanks @me", kind = MessageKind.TWITCH_USER_NOTICE, tags = mapOf("msg-id" to "resub"))
        val result = classify(msg, selfLogin = "me", parts = listOf(MessagePart.Mention("me", "@me")))
        assertEquals(Highlight(HighlightType.TWITCH_SUB), result)
    }

    @Test
    fun mentionBeatsFirstMessage() {
        val msg = hand(text = "hi @me", firstMessage = true)
        val enabled = HighlightType.DEFAULTS + HighlightType.FIRST_MESSAGE
        val result = classify(msg, enabled, "me", listOf(MessagePart.Mention("me", "@me")))
        assertEquals(Highlight(HighlightType.MENTION), result)
    }

    @Test
    fun mentionPartMatchesIgnoringCase() {
        val msg = hand(text = "@KeMuRi")
        assertEquals(
            Highlight(HighlightType.MENTION),
            classify(msg, selfLogin = "kemuri", parts = listOf(MessagePart.Mention("KeMuRi", "@KeMuRi"))),
        )
        assertNull(classify(msg, selfLogin = "other", parts = listOf(MessagePart.Mention("kemuri", "@kemuri"))))
    }

    @Test
    fun plainWordMatchesWithPunctuationStripped() {
        val self = "kemuri"
        assertEquals(Highlight(HighlightType.MENTION), classify(hand(text = "hey kemuri, you there"), selfLogin = self))
        assertEquals(Highlight(HighlightType.MENTION), classify(hand(text = "KEMURI!"), selfLogin = self))
        assertNull(classify(hand(text = "kemurixd is here"), selfLogin = self))
        assertNull(classify(hand(text = "hey kemuri"), selfLogin = null))
    }

    @Test
    fun mentionIgnoresWordsInsideNonTextParts() {
        val msg = hand(text = "https://kemuri")
        assertNull(
            classify(msg, selfLogin = "kemuri", parts = listOf(MessagePart.Link("https://kemuri", "https://kemuri"))),
        )
    }

    @Test
    fun ownMessageGetsNoMention() {
        val msg = hand(text = "I am @Kemuri", login = "kemuri")
        assertNull(classify(msg, selfLogin = "KEMURI", parts = listOf(MessagePart.Mention("kemuri", "@kemuri"))))
        assertNull(classify(hand(text = "kemuri here", login = "kemuri"), selfLogin = "kemuri"))
    }

    @Test
    fun ownMessageStillGetsOtherTypes() {
        val msg = hand(text = "kemuri", login = "kemuri", bits = 100)
        assertEquals(Highlight(HighlightType.TWITCH_CHEER), classify(msg, selfLogin = "kemuri"))
    }

    @Test
    fun kickPinnedNeverFromClassify() {
        assertNull(classify(hand(), enabled = HighlightType.entries.toSet()))
    }

    @Test
    fun plainMessageHasNoHighlight() {
        assertNull(classify(twitch(5)))
        assertNull(classify(kick(5)))
    }
}

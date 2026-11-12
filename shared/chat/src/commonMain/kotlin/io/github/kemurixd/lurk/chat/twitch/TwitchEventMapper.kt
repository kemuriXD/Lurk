package io.github.kemurixd.lurk.chat.twitch

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.ChatUser
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.NativeEmoteSpan
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.ReplyRef
import kotlin.time.Instant

/** Maps Twitch IRC lines to [ChatEvent]s. Lines Lurk has no use for (PING, NOTICE, JOIN, numerics, CAP) give null. */
object TwitchEventMapper {
    private val log = Logger.withTag("TwitchEventMapper")

    private const val ACTION_PREFIX = "\u0001ACTION "
    private const val ACTION_END = '\u0001'
    private const val MSG_PARAM_PREFIX = "msg-param-"
    private val privmsgTags = setOf("msg-id", "animation-id", "custom-reward-id")

    fun map(msg: IrcMessage): ChatEvent? =
        when (msg.command) {
            "PRIVMSG" -> chatMessage(msg, MessageKind.CHAT)?.let(ChatEvent::Message)
            "USERNOTICE" -> chatMessage(msg, MessageKind.TWITCH_USER_NOTICE)?.let(ChatEvent::Message)
            "CLEARCHAT" -> clearChat(msg)
            "CLEARMSG" -> msg.tags["target-msg-id"]?.let(ChatEvent::Removed) ?: dropped(msg)
            "ROOMSTATE" -> ChatEvent.RoomState(msg.tags)
            "RECONNECT" -> ChatEvent.ReconnectRequested
            else -> null
        }

    private fun clearChat(msg: IrcMessage): ChatEvent? =
        if (msg.params.size < 2) {
            ChatEvent.ChatCleared
        } else {
            msg.tags["target-user-id"]?.let(ChatEvent::UserCleared) ?: dropped(msg)
        }

    private fun dropped(msg: IrcMessage): ChatEvent? {
        log.w { "Dropping ${msg.command} line with missing tags" }
        return null
    }

    /**
     * Builds the [ChatMessage] for a PRIVMSG or USERNOTICE. A line without `id` or `room-id` is dropped. A missing or
     * non-numeric `tmi-sent-ts` falls back to the Unix epoch, since the mapper has no clock.
     */
    private fun chatMessage(
        msg: IrcMessage,
        kind: MessageKind,
    ): ChatMessage? {
        val id = msg.tags["id"]
        val channelId = msg.tags["room-id"]
        if (id.isNullOrEmpty() || channelId.isNullOrEmpty()) {
            dropped(msg)
            return null
        }
        val trailing = if (msg.params.size >= 2) msg.params.last() else ""
        val isChat = kind == MessageKind.CHAT
        val action = isChat && trailing.startsWith(ACTION_PREFIX)
        val text = if (action) trailing.removePrefix(ACTION_PREFIX).removeSuffix(ACTION_END.toString()) else trailing
        return ChatMessage(
            id = id,
            platform = Platform.TWITCH,
            channelId = channelId,
            sender = sender(msg),
            text = text,
            sentAt = msg.tags["tmi-sent-ts"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds) ?: EPOCH,
            kind = kind,
            nativeEmotes = TwitchEmotes.spans(msg.tags["emotes"], text),
            systemText = msg.tags["system-msg"]?.takeIf { it.isNotEmpty() },
            reply = reply(msg.tags),
            bits = msg.tags["bits"]?.toIntOrNull() ?: 0,
            firstMessage = msg.tags["first-msg"] == "1",
            action = action,
            tags = keptTags(msg.tags, isChat),
        )
    }

    /** PRIVMSG keeps the tags the renderer reads; USERNOTICE keeps `msg-id` and every `msg-param-*`. */
    private fun keptTags(
        tags: Map<String, String>,
        isChat: Boolean,
    ): Map<String, String> =
        if (isChat) {
            tags.filterKeys { it in privmsgTags }
        } else {
            tags.filterKeys { it == "msg-id" || it.startsWith(MSG_PARAM_PREFIX) }
        }

    private fun sender(msg: IrcMessage): ChatUser? {
        val login =
            msg.tags["login"]?.takeIf { it.isNotEmpty() }
                ?: msg.prefix?.substringBefore('!', missingDelimiterValue = "")
        val userId = msg.tags["user-id"]?.takeIf { it.isNotEmpty() }
        if (userId == null || login.isNullOrEmpty()) return null
        return ChatUser(
            id = userId,
            login = login,
            displayName = msg.tags["display-name"]?.takeIf { it.isNotEmpty() } ?: login,
            color = msg.tags["color"]?.takeIf { it.isNotEmpty() },
            badges = badges(msg.tags["badges"]),
        )
    }

    /** `set/version,…`. Set ids are free-form (such as `campaign-<n>-<uuid>-sub`), so they are never checked. */
    private fun badges(raw: String?): List<Badge> =
        raw.orEmpty().split(',').mapNotNull { item ->
            val slash = item.indexOf('/')
            if (slash <= 0) null else Badge(item.substring(0, slash), item.substring(slash + 1))
        }

    private fun reply(tags: Map<String, String>): ReplyRef? {
        val parentId = tags["reply-parent-msg-id"]?.takeIf { it.isNotEmpty() } ?: return null
        return ReplyRef(parentId, tags["reply-parent-user-login"], tags["reply-parent-msg-body"])
    }

    private val EPOCH = Instant.fromEpochMilliseconds(0)
}

/**
 * Reads the Twitch `emotes` tag (`id:a-b,c-d/id2:e-f`). Offsets are Unicode code points with an inclusive end, and
 * relative to the visible text: for a `/me` line that is the text after the ACTION wrapper is removed. (The fixture's
 * ACTION line has no emotes, so that last point follows Twitch's documented behaviour rather than a capture.)
 */
internal object TwitchEmotes {
    private val log = Logger.withTag("TwitchEmotes")
    private val scales = listOf(1 to "1.0", 2 to "2.0", 4 to "3.0")

    /** UTF-16 spans sorted by start. A range that is malformed, out of bounds or overlaps an earlier one is skipped. */
    fun spans(
        tag: String?,
        text: String,
    ): List<NativeEmoteSpan> {
        if (tag.isNullOrEmpty()) return emptyList()
        val offsets = codePointOffsets(text)
        val ranges =
            tag.split('/').flatMap { entry ->
                val id = entry.substringBefore(':', missingDelimiterValue = "")
                if (id.isEmpty()) {
                    emptyList()
                } else {
                    entry.substringAfter(':').split(',').mapNotNull { range ->
                        toUtf16(range, offsets)?.let { id to it }
                    }
                }
            }
        val accepted = mutableListOf<NativeEmoteSpan>()
        for ((id, span) in ranges) {
            val (start, end) = span
            if (accepted.any { start < it.end && it.start < end }) {
                log.d { "Skipping overlapping emote range" }
            } else {
                accepted += NativeEmoteSpan(start, end, emote(id, text.substring(start, end)))
            }
        }
        return accepted.sortedBy { it.start }
    }

    /** Converts `a-b` (code points, inclusive) to a UTF-16 `start until end` pair, or null when it does not fit. */
    private fun toUtf16(
        range: String,
        offsets: IntArray,
    ): Pair<Int, Int>? {
        val start = range.substringBefore('-').toIntOrNull() ?: -1
        val end = range.substringAfter('-', missingDelimiterValue = "").toIntOrNull() ?: -1
        val fits = start in 0..end && end < offsets.size - 1
        if (!fits) log.d { "Skipping emote range outside the message" }
        return if (fits) offsets[start] to offsets[end + 1] else null
    }

    /** The UTF-16 index where each code point starts, followed by `text.length`. */
    private fun codePointOffsets(text: String): IntArray {
        val offsets = ArrayList<Int>(text.length + 1)
        var i = 0
        while (i < text.length) {
            offsets += i
            val pair = text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
            i += if (pair) 2 else 1
        }
        offsets += text.length
        return offsets.toIntArray()
    }

    private fun emote(
        id: String,
        name: String,
    ): Emote =
        Emote(
            id = id,
            name = name,
            provider = EmoteProvider.TWITCH,
            scope = EmoteScope.GLOBAL,
            images =
                scales.map { (scale, size) ->
                    EmoteImage(scale, "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/$size")
                },
        )
}

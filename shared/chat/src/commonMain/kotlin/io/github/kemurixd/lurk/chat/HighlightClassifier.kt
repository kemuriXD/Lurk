package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.HighlightType
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.MessagePart

/** A built-in highlight. [color] is Twitch's `msg-param-color` value as received; the renderer maps it to a colour. */
data class Highlight(
    val type: HighlightType,
    val color: String? = null,
)

/** Picks the first enabled highlight, in [HighlightType] order, whose signal matches the message. */
object HighlightClassifier {
    private val subNotices = setOf("sub", "resub", "subgift", "submysterygift")
    private val kickSubKinds =
        setOf(MessageKind.KICK_SUBSCRIPTION, MessageKind.KICK_GIFTED_SUBS, MessageKind.KICK_LUCKY_GIFTEES)

    /** Twitch `msg-id` values that map straight to a type. */
    private val msgIdTypes =
        mapOf(
            "announcement" to HighlightType.TWITCH_ANNOUNCEMENT,
            "highlighted-message" to HighlightType.CHANNEL_POINTS_HIGHLIGHT,
            "animated-message" to HighlightType.MESSAGE_EFFECT,
            "gigantified-emote-message" to HighlightType.GIGANTIFIED_EMOTE,
            "skip-subs-mode-message" to HighlightType.SUB_ONLY_MODE,
        )

    fun classify(
        message: ChatMessage,
        parts: List<MessagePart>,
        enabled: Set<HighlightType>,
        selfLogin: String?,
    ): Highlight? {
        val type =
            HighlightType.entries.firstOrNull { it in enabled && matches(it, message, parts, selfLogin) }
                ?: return null
        val color = if (type == HighlightType.TWITCH_ANNOUNCEMENT) message.tags["msg-param-color"] else null
        return Highlight(type, color)
    }

    private fun matches(
        type: HighlightType,
        message: ChatMessage,
        parts: List<MessagePart>,
        selfLogin: String?,
    ): Boolean =
        when (type) {
            HighlightType.TWITCH_SUB -> {
                message.kind == MessageKind.TWITCH_USER_NOTICE && message.tags["msg-id"] in subNotices
            }

            HighlightType.KICK_SUB -> {
                message.kind in kickSubKinds
            }

            HighlightType.KICKS_GIFT -> {
                message.kind == MessageKind.KICKS_GIFT
            }

            HighlightType.KICK_HOST -> {
                message.kind == MessageKind.KICK_HOST
            }

            HighlightType.KICK_REWARD -> {
                message.kind == MessageKind.KICK_REWARD
            }

            HighlightType.TWITCH_CHEER -> {
                message.bits > 0
            }

            HighlightType.MENTION -> {
                mentions(message, parts, selfLogin)
            }

            HighlightType.FIRST_MESSAGE -> {
                message.firstMessage
            }

            HighlightType.KICK_PINNED -> {
                false
            }

            else -> {
                msgIdTypes[message.tags["msg-id"]] == type
            }
        }

    private fun mentions(
        message: ChatMessage,
        parts: List<MessagePart>,
        selfLogin: String?,
    ): Boolean {
        val isSelf = message.sender?.login.equals(selfLogin, ignoreCase = true)
        return !selfLogin.isNullOrEmpty() && !isSelf && parts.any { partMentions(it, selfLogin) }
    }

    private fun partMentions(
        part: MessagePart,
        selfLogin: String,
    ): Boolean =
        when (part) {
            is MessagePart.Mention -> part.login.equals(selfLogin, ignoreCase = true)
            is MessagePart.Text -> part.text.split(' ').any { wordIs(it, selfLogin) }
            else -> false
        }

    private fun wordIs(
        word: String,
        selfLogin: String,
    ): Boolean = word.trim { !it.isLetterOrDigit() && it != '_' }.equals(selfLogin, ignoreCase = true)
}

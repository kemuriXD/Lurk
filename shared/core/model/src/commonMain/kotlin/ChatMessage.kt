package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ChatUser(
    val id: String,
    val login: String,
    val displayName: String,
    val color: String? = null,
    val badges: List<Badge> = emptyList(),
)

/** An emote the platform marked up itself. Indices are UTF-16 offsets into [ChatMessage.text]; [end] is exclusive. */
@Serializable
data class NativeEmoteSpan(
    val start: Int,
    val end: Int,
    val emote: Emote,
)

@Serializable
data class ReplyRef(
    val parentId: String,
    val parentLogin: String?,
    val parentText: String?,
)

@Serializable
enum class MessageKind {
    CHAT,
    TWITCH_USER_NOTICE,
    KICK_SUBSCRIPTION,
    KICK_GIFTED_SUBS,
    KICK_LUCKY_GIFTEES,
    KICKS_GIFT,
    KICK_HOST,
    KICK_REWARD,
    SYSTEM,
}

/**
 * A platform-neutral chat line. [tags] holds the platform extras the classifier and renderer read: Twitch `msg-id`,
 * `msg-param-color`, `animation-id`, and Kick event fields such as `months`, `gift_name` and `amount`.
 */
@Serializable
data class ChatMessage(
    val id: String,
    val platform: Platform,
    val channelId: String,
    val sender: ChatUser?,
    val text: String,
    val sentAt: Instant,
    val kind: MessageKind = MessageKind.CHAT,
    val nativeEmotes: List<NativeEmoteSpan> = emptyList(),
    val systemText: String? = null,
    val reply: ReplyRef? = null,
    val bits: Int = 0,
    val firstMessage: Boolean = false,
    val action: Boolean = false,
    val historical: Boolean = false,
    val tags: Map<String, String> = emptyMap(),
)

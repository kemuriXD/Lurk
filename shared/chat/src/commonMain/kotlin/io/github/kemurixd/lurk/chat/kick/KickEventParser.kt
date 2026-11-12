package io.github.kemurixd.lurk.chat.kick

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.ChatUser
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.ReplyRef
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Instant

/**
 * Parses Kick realtime frames. A frame holds one or more newline-separated Centrifugo replies; only
 * `push.pub.data` replies carry events, and their `data.data` is a JSON string that is decoded a second time.
 * Nothing here throws: a reply that is not JSON, or whose inner payload is broken, is skipped.
 */
object KickEventParser {
    private val log = Logger.withTag("KickEventParser")

    private const val EVENT_PREFIX = "App\\Events\\"

    fun parseFrame(frame: String): List<ChatEvent> = frame.lineSequence().flatMap(::parseReply).toList()

    private fun parseReply(reply: String): Sequence<ChatEvent> {
        if (reply.isBlank()) return emptySequence()
        return try {
            val root = Json.parseToJsonElement(reply) as? JsonObject
            val push = root?.obj("push")
            val data = push?.obj("pub")?.obj("data")
            val event = data?.string("event")
            if (data == null || event == null) {
                emptySequence()
            } else {
                val payload = decodePayload(data["data"])
                val channel = push.string("channel")
                if (payload == null) emptySequence() else sequenceOfNotNull(map(event, payload, channel))
            }
        } catch (e: SerializationException) {
            log.w { "Skipping a reply that is not valid JSON: ${e.message}" }
            emptySequence()
        } catch (e: IllegalArgumentException) {
            log.w { "Skipping a reply with an unexpected shape: ${e.message}" }
            emptySequence()
        }
    }

    private fun sequenceOfNotNull(event: ChatEvent?): Sequence<ChatEvent> =
        if (event == null) emptySequence() else sequenceOf(event)

    /** The inner payload is normally a JSON string; an already-decoded object is accepted too. */
    private fun decodePayload(element: JsonElement?): JsonObject? =
        when (element) {
            is JsonObject -> element
            is JsonPrimitive -> if (element.isString) Json.parseToJsonElement(element.content) as? JsonObject else null
            else -> null
        }

    private fun map(
        event: String,
        payload: JsonObject,
        channel: String?,
    ): ChatEvent? {
        val name = event.removePrefix(EVENT_PREFIX)
        val room = KickMessages.chatroomFromChannel(channel)
        return when (name) {
            "ChatMessageEvent" -> {
                KickMessages.chat(payload, room)?.let(ChatEvent::Message)
            }

            "MessageDeletedEvent" -> {
                payload.obj("message")?.string("id")?.let(ChatEvent::Removed)
            }

            "UserBannedEvent" -> {
                payload.obj("user")?.string("id")?.let(ChatEvent::UserCleared)
            }

            "ChatroomClearEvent" -> {
                ChatEvent.ChatCleared
            }

            "PinnedMessageCreatedEvent" -> {
                payload.obj("message")?.let { KickMessages.chat(it, room) }?.let(ChatEvent::Pinned)
            }

            "PinnedMessageDeletedEvent" -> {
                ChatEvent.Unpinned(null)
            }

            else -> {
                noticeKind(name)?.let { notice(name, it, payload, room) }
            }
        }
    }

    private fun noticeKind(name: String): MessageKind? =
        when (name) {
            "SubscriptionEvent" -> MessageKind.KICK_SUBSCRIPTION
            "GiftedSubscriptionsEvent" -> MessageKind.KICK_GIFTED_SUBS
            "LuckyUsersWhoGotGiftSubscriptionsEvent" -> MessageKind.KICK_LUCKY_GIFTEES
            "KicksGifted" -> MessageKind.KICKS_GIFT
            "StreamHostEvent", "ChatMoveToSupportedChannelEvent" -> MessageKind.KICK_HOST
            "RewardRedeemedEvent" -> MessageKind.KICK_REWARD
            else -> null
        }

    private fun noticeSender(
        name: String,
        payload: JsonObject,
    ): ChatUser? =
        when (name) {
            "SubscriptionEvent", "RewardRedeemedEvent" -> {
                KickMessages.user(payload, "user_id", "username")
            }

            "GiftedSubscriptionsEvent", "LuckyUsersWhoGotGiftSubscriptionsEvent" -> {
                KickMessages.user(payload, null, "gifter_username")
            }

            "KicksGifted" -> {
                payload.obj("sender")?.let { KickMessages.user(it, "id", "username", "username_color") }
            }

            "StreamHostEvent" -> {
                KickMessages.user(payload, null, "host_username")
            }

            else -> {
                payload.obj("hosted")?.let { KickMessages.user(it, "id", "username") }
            }
        }

    private fun notice(
        event: String,
        kind: MessageKind,
        payload: JsonObject,
        room: String?,
    ): ChatEvent {
        val createdAt = payload.string("created_at")
        val id =
            payload.string("id")
                ?: payload.string("gift_transaction_id")
                ?: "kick:$event:${createdAt ?: payload.toString().hashCode()}"
        val text =
            when (kind) {
                MessageKind.KICKS_GIFT -> payload.string("message").orEmpty()
                MessageKind.KICK_REWARD -> payload.string("user_input").orEmpty()
                else -> ""
            }
        val channelId =
            payload.string("chatroom_id")
                ?: room
                ?: payload.string("channel_id")
                ?: payload.obj("channel")?.string("id")
                ?: ""
        return ChatEvent.Message(
            ChatMessage(
                id = id,
                platform = Platform.KICK,
                channelId = channelId,
                sender = noticeSender(event, payload),
                text = text,
                sentAt = KickMessages.time(payload["created_at"]),
                kind = kind,
                tags = KickMessages.tags(payload),
            ),
        )
    }
}

/** Shared mapping of Kick chat messages, used by the realtime and history parsers. */
internal object KickMessages {
    private val log = Logger.withTag("KickMessages")
    private val epoch = Instant.fromEpochMilliseconds(0)
    private val roomChannel = Regex("""^chatrooms?[._](\d+)""")

    /** The chatroom id in a `chatrooms.<id>.v2` or `chatroom_<id>` channel name, or null for any other channel. */
    fun chatroomFromChannel(channel: String?): String? = channel?.let { roomChannel.find(it)?.groupValues?.get(1) }

    /** A `ChatMessageEvent` payload, or a history item. Returns null when it has no id or no readable sender. */
    fun chat(
        obj: JsonObject,
        fallbackRoom: String?,
    ): ChatMessage? {
        val id = obj.string("id")
        val sender = obj.obj("sender")?.let(::sender)
        if (id == null || sender == null) return null
        val metadata = metadata(obj["metadata"])
        val (text, spans) = KickContent.split(obj.string("content").orEmpty())
        val reply = if (obj.string("type") == "reply") reply(obj, metadata) else null
        return ChatMessage(
            id = id,
            platform = Platform.KICK,
            channelId = obj.string("chatroom_id") ?: obj.string("chat_id") ?: fallbackRoom ?: "",
            sender = sender,
            text = text,
            sentAt = time(obj["created_at"]),
            kind = MessageKind.CHAT,
            nativeEmotes = spans,
            reply = reply,
        )
    }

    /** History items carry `metadata` as a JSON string; live payloads carry an object. */
    private fun metadata(element: JsonElement?): JsonObject? =
        when (element) {
            is JsonObject -> {
                element
            }

            is JsonPrimitive -> {
                if (element.isString) {
                    try {
                        Json.parseToJsonElement(element.content) as? JsonObject
                    } catch (e: SerializationException) {
                        log.d { "Ignoring unreadable message metadata: ${e.message}" }
                        null
                    }
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }

    private fun reply(
        obj: JsonObject,
        metadata: JsonObject?,
    ): ReplyRef? {
        val original = metadata?.obj("original_message")
        val parentId = original?.string("id") ?: obj.string("thread_parent_id") ?: return null
        return ReplyRef(parentId, metadata?.obj("original_sender")?.string("username"), original?.string("content"))
    }

    private fun sender(obj: JsonObject): ChatUser? {
        val username = obj.string("username")
        val id = obj.string("id")
        if (username == null || id == null) return null
        val identity = obj.obj("identity")
        return ChatUser(
            id = id,
            login = username,
            displayName = username,
            color = identity?.string("color"),
            badges = (identity?.get("badges") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::badge) },
        )
    }

    private fun badge(obj: JsonObject): Badge? {
        val type = obj.string("type") ?: return null
        return Badge(type, obj.string("count") ?: "1", obj.string("text"))
    }

    /** A user from `<idKey>`, `<nameKey>` and optional `<colorKey>` fields. Without an id the username is the id. */
    fun user(
        obj: JsonObject,
        idKey: String?,
        nameKey: String,
        colorKey: String? = null,
    ): ChatUser? {
        val name = obj.string(nameKey)
        return name?.let {
            ChatUser(
                id = idKey?.let(obj::string) ?: it,
                login = it,
                displayName = it,
                color = colorKey?.let(obj::string),
            )
        }
    }

    /** ISO-8601 or epoch seconds; anything else is the Unix epoch, since the parser has no clock. */
    fun time(element: JsonElement?): Instant {
        val raw = (element as? JsonPrimitive)?.contentOrNull ?: return epoch
        val seconds = raw.toLongOrNull()
        return try {
            if (seconds != null) Instant.fromEpochSeconds(seconds) else Instant.parse(raw)
        } catch (e: IllegalArgumentException) {
            log.d { "Unreadable timestamp: ${e.message}" }
            epoch
        }
    }

    /**
     * The payload's top-level primitive fields, `gift.*` fields as `gift_<field>`, and arrays of primitives joined
     * with commas. Other nested objects are left out.
     */
    fun tags(payload: JsonObject): Map<String, String> =
        payload
            .flatMap { (key, value) ->
                when (value) {
                    is JsonArray -> listOf(key to value.mapNotNull(::scalar).joinToString(","))
                    is JsonObject -> if (key == "gift") giftTags(value) else emptyList()
                    else -> listOfNotNull(scalar(value)?.let { key to it })
                }
            }.toMap()

    private fun giftTags(gift: JsonObject): List<Pair<String, String>> =
        gift.mapNotNull { (k, v) -> scalar(v)?.let { "gift_$k" to it } }

    private fun scalar(element: JsonElement): String? =
        if (element is JsonNull) null else (element as? JsonPrimitive)?.content
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** A primitive's text, so numbers and booleans read as strings; null for missing, JSON null and non-primitives. */
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

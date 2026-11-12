package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.Platform

/** The channel a [ChatSession] is joined to. Kick logins are lowercase. */
data class ChatChannel(
    val platform: Platform,
    val channelId: String,
    val login: String,
)

/**
 * A message ready to render. [emoteGeneration] is the [io.github.kemurixd.lurk.emotes.EmoteSnapshot.generation]
 * that [parts] were tokenized with. [deleted] marks a message a moderator removed.
 */
data class ChatItem(
    val message: ChatMessage,
    val parts: List<MessagePart>,
    val highlight: Highlight?,
    val deleted: Boolean = false,
    val emoteGeneration: Int,
)

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

/** An immutable view of a chat room: the buffered [items], oldest first, the [pinned] message and the connection. */
data class ChatSnapshot(
    val items: List<ChatItem>,
    val pinned: ChatItem?,
    val connection: ConnectionState,
)

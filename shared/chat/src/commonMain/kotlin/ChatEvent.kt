package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.ChatMessage

/** Something that happened in a chat room, from either platform. */
sealed interface ChatEvent {
    /** A chat line or a platform notice such as a sub or a raid. */
    data class Message(
        val message: ChatMessage,
    ) : ChatEvent

    /** One message was deleted by a moderator. */
    data class Removed(
        val messageId: String,
    ) : ChatEvent

    /** A user was timed out or banned; their messages should be cleared. */
    data class UserCleared(
        val userId: String,
    ) : ChatEvent

    /** The whole chat was cleared. */
    data object ChatCleared : ChatEvent

    /** A message was pinned. */
    data class Pinned(
        val message: ChatMessage,
    ) : ChatEvent

    /** The pinned message was removed; [messageId] is null when the platform does not say which one. */
    data class Unpinned(
        val messageId: String?,
    ) : ChatEvent

    /** Room settings changed. Twitch sends only the changed keys on updates, so [values] can be partial. */
    data class RoomState(
        val values: Map<String, String>,
    ) : ChatEvent

    /** The server asked the client to reconnect. */
    data object ReconnectRequested : ChatEvent
}

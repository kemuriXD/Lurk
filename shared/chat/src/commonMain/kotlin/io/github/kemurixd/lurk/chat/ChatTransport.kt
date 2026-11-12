package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.chat.kick.KickEventParser
import io.github.kemurixd.lurk.chat.kick.KickHistoryParser
import io.github.kemurixd.lurk.chat.twitch.IrcParser
import io.github.kemurixd.lurk.chat.twitch.RecentMessagesParser
import io.github.kemurixd.lurk.chat.twitch.TwitchEventMapper
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.coroutines.flow.Flow

/**
 * The raw text frames of one chat connection. The flow is cold: each collection opens one connection, and the flow
 * completes or throws when that connection drops. Cancelling the collection closes the connection. Twitch frames are
 * IRC text and may hold several CRLF-separated lines; Kick frames are Pusher/Centrifugo JSON. Implemented in P6.
 */
fun interface ChatTransport {
    fun frames(): Flow<String>
}

/** Fetches the recent chat history of one channel and returns the raw response body. Implemented in P6. */
fun interface ChatHistorySource {
    suspend fun fetch(): String
}

/** Sends a chat message, optionally as a reply to [replyToId]. Implemented in P6. */
fun interface ChatSender {
    suspend fun send(
        text: String,
        replyToId: String?,
    ): Outcome<Unit>
}

/** Turns one transport frame into chat events. Never throws; a bad frame gives an empty list. */
fun interface ChatFrameParser {
    fun parse(frame: String): List<ChatEvent>
}

/** The platform parsers behind the [ChatFrameParser] and history contracts. */
object ChatParsers {
    /** Splits a frame into IRC lines (CRLF or LF) and maps each one; lines Lurk has no use for are skipped. */
    val twitch: ChatFrameParser =
        ChatFrameParser { frame ->
            frame
                .lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
                .mapNotNull { line -> IrcParser.parse(line)?.let(TwitchEventMapper::map) }
                .toList()
        }

    val kick: ChatFrameParser = ChatFrameParser(KickEventParser::parseFrame)

    fun frames(platform: Platform): ChatFrameParser =
        when (platform) {
            Platform.TWITCH -> twitch
            Platform.KICK -> kick
        }

    /** Parses a history body from [ChatHistorySource]; messages come back oldest first with `historical = true`. */
    fun history(platform: Platform): (String) -> List<ChatEvent> =
        when (platform) {
            Platform.TWITCH -> RecentMessagesParser::parse
            Platform.KICK -> KickHistoryParser::parse
        }
}

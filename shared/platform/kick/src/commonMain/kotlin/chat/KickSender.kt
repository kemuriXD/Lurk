package io.github.kemurixd.lurk.platform.kick.chat

import io.github.kemurixd.lurk.chat.ChatSender
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** The message a reply answers: what the web app puts in `metadata`. */
data class KickReplyContext(
    val messageId: String,
    val content: String,
    val senderId: String,
    val senderUsername: String,
)

/**
 * Sends chat messages to one chatroom with the user's token. A reply whose original message [replyContext] does not
 * know goes out as a plain message. Signed out is `NotSignedIn`. Not a data class, so the token can't leak through
 * `toString()`.
 */
class KickSender(
    private val api: KickApi,
    private val chatroomId: String,
    private val token: suspend () -> String?,
    private val replyContext: suspend (String) -> KickReplyContext?,
    private val clock: Clock,
) : ChatSender {
    override suspend fun send(
        text: String,
        replyToId: String?,
    ): Outcome<Unit> {
        val bearer = token() ?: return Outcome.Failure(LurkError.NotSignedIn)
        val body = bodyOf(text, replyToId?.let { replyContext(it) })
        val path = "v2/messages/send/${chatroomId.asPathSegment()}"
        return api.send(HttpMethod.Post, KickBase.API, path, bearer, body).map { }
    }

    private fun bodyOf(
        text: String,
        reply: KickReplyContext?,
    ): JsonObject =
        buildJsonObject {
            put("content", text)
            put("type", if (reply == null) "message" else "reply")
            put("message_ref", clock.now().toEpochMilliseconds().toString())
            if (reply != null) put("metadata", metadataOf(reply))
        }

    private fun metadataOf(reply: KickReplyContext): JsonObject =
        buildJsonObject {
            put(
                "original_message",
                buildJsonObject {
                    put("id", reply.messageId)
                    put("content", reply.content)
                },
            )
            put(
                "original_sender",
                buildJsonObject {
                    put("id", reply.senderId)
                    put("username", reply.senderUsername)
                },
            )
        }

    override fun toString(): String = "KickSender"
}

package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.chat.ChatTransport
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.platform.api.authed
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Centrifugo's ping is an empty reply, and the pong an empty command. */
private const val EMPTY = "{}"
private const val CONNECT_ID = 1

/**
 * Kick chat over Centrifugo. Each collection runs the whole [RealtimeBootstrap] (so a reconnect gets a fresh
 * connection token, never a cached one), opens the socket, sends the connect command and one subscribe per public
 * channel, then emits every frame as received and answers each ping. The flow completes when the server sends a
 * disconnect, rejects the connect command or closes the socket, and P4's chat session then reconnects.
 *
 * The bootstrap runs with the session token when signed in. A 401 is reported to [sessions] once and the bootstrap
 * runs again signed out, which is enough to read chat. Any other bootstrap failure throws [LurkException]. The
 * tokens are fetched once per collection and never kept or printed.
 */
class CentrifugoTransport(
    private val bootstrap: RealtimeBootstrap,
    private val connector: WebSocketConnector,
    private val channelId: String,
    private val chatroomId: String,
    private val sessions: SessionRepository,
) : ChatTransport {
    override fun frames(): Flow<String> =
        flow {
            val ticket =
                when (val opened = openTicket()) {
                    is Outcome.Success -> opened.value
                    is Outcome.Failure -> throw LurkException(opened.error)
                }
            val connection = connector.connect(ticket.socketUrl, emptyMap())
            try {
                handshake(connection, ticket)
                emitAll(
                    connection.incoming.transformWhile { frame ->
                        answerPings(connection, frame)
                        emit(frame)
                        !isDisconnect(frame) && !isConnectError(frame)
                    },
                )
            } finally {
                withContext(NonCancellable) { connection.close() }
            }
        }

    override fun toString(): String = "CentrifugoTransport"

    private suspend fun openTicket(): Outcome<RealtimeTicket> =
        sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
            bootstrap.open(channelId, chatroomId, token, userId = null)
        }

    private suspend fun handshake(
        connection: WebSocketConnection,
        ticket: RealtimeTicket,
    ) {
        connection.send(
            command(CONNECT_ID, "connect") {
                put("token", ticket.connectToken)
                put("name", "js")
            },
        )
        ticket.channels.forEachIndexed { index, channel ->
            connection.send(command(CONNECT_ID + 1 + index, "subscribe") { put("channel", channel) })
        }
    }
}

private fun command(
    id: Int,
    method: String,
    params: JsonObjectBuilder.() -> Unit,
): String =
    buildJsonObject {
        put("id", id)
        putJsonObject(method, params)
    }.toString()

/** Replies `{}` to every ping line of [frame]; Centrifugo may batch several replies into one frame. */
private suspend fun answerPings(
    connection: WebSocketConnection,
    frame: String,
) {
    frame.lineSequence().filter { it.trim() == EMPTY }.forEach { _ -> connection.send(EMPTY) }
}

/**
 * True when a line of [frame] is a server disconnect, either `{"disconnect":…}` or `{"push":{"disconnect":…}}`.
 * Chat text that merely contains the word does not count: only the reply's own keys are read.
 */
internal fun isDisconnect(frame: String): Boolean =
    replies(frame, "disconnect").any { reply ->
        "disconnect" in reply || (reply["push"] as? JsonObject)?.containsKey("disconnect") == true
    }

/** True when a line of [frame] is the server's error reply to the connect command: `{"id":1,"error":…}`. */
internal fun isConnectError(frame: String): Boolean =
    replies(frame, "error").any { reply ->
        "error" in reply && (reply["id"] as? JsonPrimitive)?.intOrNull == CONNECT_ID
    }

/** The lines of [frame] that mention [word] and parse as JSON objects. */
private fun replies(
    frame: String,
    word: String,
): Sequence<JsonObject> =
    frame.lineSequence().filter { word in it }.mapNotNull { line ->
        try {
            Json.parseToJsonElement(line) as? JsonObject
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            null
        }
    }

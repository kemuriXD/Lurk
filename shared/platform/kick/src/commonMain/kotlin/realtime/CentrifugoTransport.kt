package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.chat.ChatTransport
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Centrifugo's ping is an empty reply, and the pong an empty command. */
private const val EMPTY = "{}"
private const val CONNECT_ID = 1

/**
 * Kick chat over Centrifugo. Each collection runs the whole [RealtimeBootstrap] (so a reconnect gets a fresh
 * connection token, never a cached one), opens the socket, sends the connect command and one subscribe per public
 * channel, then emits every frame as received and answers each ping. The flow completes when the server sends a
 * disconnect or closes the socket, and P4's chat session then reconnects. A bootstrap failure throws
 * [LurkException]. The token is fetched once per collection and never kept or printed.
 */
class CentrifugoTransport(
    private val bootstrap: RealtimeBootstrap,
    private val connector: WebSocketConnector,
    private val channelId: String,
    private val chatroomId: String,
    private val token: suspend () -> String?,
) : ChatTransport {
    override fun frames(): Flow<String> =
        flow {
            val ticket =
                when (val opened = bootstrap.open(channelId, chatroomId, token(), userId = null)) {
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
                        !isDisconnect(frame)
                    },
                )
            } finally {
                withContext(NonCancellable) { connection.close() }
            }
        }

    override fun toString(): String = "CentrifugoTransport"

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
    frame.lineSequence().any { line ->
        val reply =
            try {
                if (line.contains("disconnect")) Json.parseToJsonElement(line) as? JsonObject else null
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        reply != null && ("disconnect" in reply || (reply["push"] as? JsonObject)?.containsKey("disconnect") == true)
    }

package io.github.kemurixd.lurk.emotes.seventv

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.emotes.provider.parseObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A server message from the 7TV EventAPI v3; the opcode is noted on each type. */
sealed interface EventApiMessage {
    /** Op 1, the first message on every connection. */
    data class Hello(
        val sessionId: String,
        val heartbeatInterval: Duration,
    ) : EventApiMessage

    /** Op 2. */
    data object Heartbeat : EventApiMessage

    /** Op 4: the server asks the client to reconnect. */
    data object Reconnect : EventApiMessage

    /** Op 5: the answer to a client command; [success] is only set for some commands, such as RESUME. */
    data class Ack(
        val command: String,
        val success: Boolean?,
    ) : EventApiMessage

    /** Op 7: the server is closing the stream. */
    data class EndOfStream(
        val code: Int,
        val message: String?,
    ) : EventApiMessage

    /** Op 0: a subscribed event. [body] is mapped by [SevenTvDispatch]. */
    data class Dispatch(
        val type: String,
        val body: JsonObject,
    ) : EventApiMessage

    /** Anything else: an unknown opcode, or a message that is missing what its opcode needs. */
    data object Unknown : EventApiMessage
}

object EventApiParser {
    private val log = Logger.withTag("EventApiParser")
    private const val OP_DISPATCH = 0
    private const val OP_HELLO = 1
    private const val OP_HEARTBEAT = 2
    private const val OP_RECONNECT = 4
    private const val OP_ACK = 5
    private const val OP_END_OF_STREAM = 7

    /** Never throws: text that is not a known message becomes [EventApiMessage.Unknown]. */
    fun parse(text: String): EventApiMessage {
        val root = parseObject(text, log) ?: return EventApiMessage.Unknown
        val d = root["d"] as? JsonObject
        return when (root.int("op")) {
            OP_DISPATCH -> dispatch(d)
            OP_HELLO -> hello(d)
            OP_HEARTBEAT -> EventApiMessage.Heartbeat
            OP_RECONNECT -> EventApiMessage.Reconnect
            OP_ACK -> ack(d)
            OP_END_OF_STREAM -> EventApiMessage.EndOfStream(d?.int("code") ?: 0, d?.string("message"))
            else -> null
        } ?: EventApiMessage.Unknown
    }

    private fun dispatch(d: JsonObject?): EventApiMessage? {
        val type = d?.string("type")
        val body = d?.obj("body")
        return if (type != null && body != null) EventApiMessage.Dispatch(type, body) else null
    }

    private fun hello(d: JsonObject?): EventApiMessage? {
        val session = d?.string("session_id")
        val interval = d?.long("heartbeat_interval")
        return if (session != null && interval != null) EventApiMessage.Hello(session, interval.milliseconds) else null
    }

    private fun ack(d: JsonObject?): EventApiMessage? {
        val command = d?.string("command") ?: return null
        val success = ((d["data"] as? JsonObject)?.get("success") as? JsonPrimitive)?.booleanOrNull
        return EventApiMessage.Ack(command, success)
    }
}

private fun JsonElement?.primitive(): JsonPrimitive? = this as? JsonPrimitive

internal fun JsonObject.string(key: String): String? = get(key).primitive()?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.int(key: String): Int? = get(key).primitive()?.takeIf { !it.isString }?.intOrNull

internal fun JsonObject.long(key: String): Long? = get(key).primitive()?.takeIf { !it.isString }?.longOrNull

internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

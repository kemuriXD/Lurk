package io.github.kemurixd.lurk.chat.twitch

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.chat.ChatEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses the recent-messages service body (`{"messages": ["<irc line>", …], "error": …}`). Each line goes
 * through [IrcParser] and [TwitchEventMapper]; messages come back with `historical = true`. A bad body gives
 * an empty list and a bad line is skipped.
 */
object RecentMessagesParser {
    private val log = Logger.withTag("RecentMessagesParser")

    fun parse(json: String): List<ChatEvent> {
        val lines = messages(json) ?: return emptyList()
        return lines.mapNotNull { element ->
            val line = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            line?.let(IrcParser::parse)?.let(TwitchEventMapper::map)?.let(::markHistorical)
        }
    }

    private fun markHistorical(event: ChatEvent): ChatEvent =
        if (event is ChatEvent.Message) ChatEvent.Message(event.message.copy(historical = true)) else event

    private fun messages(json: String): JsonArray? {
        val root =
            try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                log.w { "Recent messages body is not JSON: ${e.message}" }
                return null
            }
        val messages = (root as? JsonObject)?.get("messages") as? JsonArray
        if (messages == null) log.w { "Recent messages body has no messages array" }
        return messages
    }
}

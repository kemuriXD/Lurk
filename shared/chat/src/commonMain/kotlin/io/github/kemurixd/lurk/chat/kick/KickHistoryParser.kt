package io.github.kemurixd.lurk.chat.kick

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.chat.ChatEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Parses the Kick chat history body (`{"data": {"messages": [...]}}`). The service lists newest first; the result is
 * oldest first with `historical = true`. A bad body gives an empty list and a bad item is skipped.
 */
object KickHistoryParser {
    private val log = Logger.withTag("KickHistoryParser")

    fun parse(json: String): List<ChatEvent> {
        val items = messages(json) ?: return emptyList()
        return items
            .mapNotNull { (it as? JsonObject)?.let { obj -> KickMessages.chat(obj, null) } }
            .map { ChatEvent.Message(it.copy(historical = true)) }
            .reversed()
            .sortedBy { it.message.sentAt }
    }

    private fun messages(json: String): JsonArray? {
        val root =
            try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                log.w { "Kick history body is not JSON (${e::class.simpleName})" }
                return null
            }
        val messages = (root as? JsonObject)?.obj("data")?.get("messages") as? JsonArray
        if (messages == null) log.w { "Kick history body has no messages array" }
        return messages
    }
}

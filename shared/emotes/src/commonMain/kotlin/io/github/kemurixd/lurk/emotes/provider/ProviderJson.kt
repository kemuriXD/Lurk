package io.github.kemurixd.lurk.emotes.provider

import co.touchlab.kermit.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** Lenient `Json` shared by the provider parsers: unknown keys are ignored and absent fields fall back to defaults. */
internal val providerJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

/**
 * Parses [body] into a [JsonObject], or returns null (and logs) when it is not a JSON object. Logs name only the
 * exception class: kotlinx messages quote the input, and bodies can carry user names.
 */
internal fun parseObject(
    body: String,
    log: Logger,
): JsonObject? =
    try {
        providerJson.parseToJsonElement(body).jsonObject
    } catch (e: SerializationException) {
        log.w { "Response is not a JSON object (${e::class.simpleName})" }
        null
    } catch (e: IllegalArgumentException) {
        log.w { "Response is not a JSON object (${e::class.simpleName})" }
        null
    }

/** Parses [body] into a [JsonArray], or returns null (and logs) when it is not a JSON array. */
internal fun parseArray(
    body: String,
    log: Logger,
): JsonArray? =
    try {
        val element = providerJson.parseToJsonElement(body)
        if (element is JsonArray) {
            element
        } else {
            log.w { "Response is not a JSON array" }
            null
        }
    } catch (e: SerializationException) {
        log.w { "Response is not a JSON array (${e::class.simpleName})" }
        null
    }

/** Decodes each element on its own; an element that fails is logged and skipped, the others are kept. */
internal inline fun <reified T, R : Any> JsonElement?.decodeEach(
    log: Logger,
    map: (T) -> R?,
): List<R> {
    val items = this as? JsonArray ?: return emptyList()
    return items.mapNotNull { element ->
        try {
            map(providerJson.decodeFromJsonElement<T>(element))
        } catch (e: SerializationException) {
            log.w { "Skipping emote that failed to decode (${e::class.simpleName})" }
            null
        } catch (e: IllegalArgumentException) {
            log.w { "Skipping emote that failed to decode (${e::class.simpleName})" }
            null
        }
    }
}

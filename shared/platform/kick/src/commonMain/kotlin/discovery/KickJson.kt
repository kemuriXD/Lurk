package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

@PublishedApi
internal val lenientJson = Json { ignoreUnknownKeys = true }

/** Decodes one list item, or null when it does not fit [T]. A broken item must not cost the rest of the list. */
internal inline fun <reified T : Any> JsonElement.decodeOrNull(): T? =
    try {
        lenientJson.decodeFromJsonElement<T>(this)
    } catch (
        @Suppress("SwallowedException") e: SerializationException,
    ) {
        null
    }

/** Runs [read] on a successful reply. A reply that is not the expected shape comes back as `SchemaChanged`. */
internal suspend fun <T> Outcome<JsonElement>.decode(read: (JsonElement) -> T): Outcome<T> =
    when (this) {
        is Outcome.Failure -> this
        is Outcome.Success -> outcomeOf { read(value) }
    }

internal fun JsonElement.asObject(): JsonObject =
    this as? JsonObject ?: throw SerializationException("expected an object")

internal fun JsonObject.requireObject(key: String): JsonObject =
    this[key] as? JsonObject ?: throw SerializationException("expected an object at $key")

internal fun JsonObject.requireArray(key: String): JsonArray =
    this[key] as? JsonArray ?: throw SerializationException("expected an array at $key")

/** The array at [key], or an empty one when it is missing. */
internal fun JsonObject.arrayOrEmpty(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

/** The text of a primitive field. JSON null reads as null. */
internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

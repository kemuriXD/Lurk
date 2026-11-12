package io.github.kemurixd.lurk.platform.twitch.discovery

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.nonBlank(key: String): String? = text(key)?.takeIf { it.isNotBlank() }

internal fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull

internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

/** The field [key] as an object. Missing or null means the response is not the shape we expect. */
internal fun JsonObject.requireObj(key: String): JsonObject = checkNotNull(obj(key)) { "response has no $key" }

internal fun instantOrNull(text: String?): Instant? =
    try {
        text?.let(Instant::parse)
    } catch (
        @Suppress("SwallowedException") e: IllegalArgumentException,
    ) {
        null
    }

internal fun loginOrNull(fields: JsonObject): String? =
    fields.text("login")?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }

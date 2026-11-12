package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The field [key] as the objects of an array. A missing field, or an entry that is not an object, adds none. */
internal fun JsonObject.objects(key: String): List<JsonObject> =
    (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

/** The value, or a [LurkException] carrying the failure, which is how the asset sources report errors. */
internal fun <T> Outcome<T>.valueOrThrow(): T =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> throw LurkException(error)
    }

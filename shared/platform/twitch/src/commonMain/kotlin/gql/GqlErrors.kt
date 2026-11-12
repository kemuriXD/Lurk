@file:Suppress("ktlint:standard:filename")

package io.github.kemurixd.lurk.platform.twitch.gql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val authMarkers = listOf("OAuth token", "Unauthorized")
private val integrityMarkers = listOf("failed integrity check", "IntegrityCheckFailed")

/** What one GQL response says, before any caller decodes it. */
internal sealed interface GqlReply {
    data class Data(
        val data: JsonObject,
    ) : GqlReply

    data object AuthExpired : GqlReply

    data object IntegrityFailed : GqlReply

    /** Not the shape GQL answers with, or errors without data. */
    data object UnexpectedShape : GqlReply
}

/** Reads a parsed response body: a one-element array, or the bare object some failures return. */
internal fun classifyReply(body: JsonElement): GqlReply {
    val reply =
        when (body) {
            is JsonArray -> body.firstOrNull() as? JsonObject
            is JsonObject -> body
            else -> null
        } ?: return GqlReply.UnexpectedShape
    val texts = errorTexts(reply)
    return when {
        texts.any { text -> authMarkers.any { it in text } } -> GqlReply.AuthExpired
        texts.any { text -> integrityMarkers.any { it in text } } -> GqlReply.IntegrityFailed
        else -> (reply["data"] as? JsonObject)?.let { GqlReply.Data(it) } ?: GqlReply.UnexpectedShape
    }
}

/** Every message and error code in a response, to match markers against. */
private fun errorTexts(reply: JsonObject): List<String> {
    val out = ArrayList<String>()
    out.addText(reply["message"])
    out.addText(reply["error"])
    (reply["errors"] as? JsonArray)?.forEach { error ->
        val obj = error as? JsonObject ?: return@forEach
        out.addText(obj["message"])
        out.addText((obj["extensions"] as? JsonObject)?.get("code"))
    }
    return out
}

private fun MutableList<String>.addText(element: JsonElement?) {
    (element as? JsonPrimitive)?.contentOrNull?.let { add(it) }
}

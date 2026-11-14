@file:Suppress("ktlint:standard:filename")

package io.github.kemurixd.lurk.platform.twitch.gql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val authMarkers = listOf("OAuth token", "Unauthorized")
private val integrityMarkers = listOf("failed integrity check", "IntegrityCheckFailed")

/** What GraphQL says when the query itself no longer fits the server's schema: asking again can't help. */
private val schemaMarkers =
    listOf(
        "Cannot query field",
        "Unknown argument",
        "Unknown type",
        "GRAPHQL_VALIDATION_FAILED",
        "PersistedQueryNotFound",
    )

/** What one GQL response says, before any caller decodes it. */
internal sealed interface GqlReply {
    /** The `data` object. [failed] when the reply also listed errors, so a part of [data] may be missing. */
    data class Data(
        val data: JsonObject,
        val failed: Boolean = false,
    ) : GqlReply

    data object AuthExpired : GqlReply

    data object IntegrityFailed : GqlReply

    /** The server listed errors and sent no data: a backend failure such as a timeout, worth asking again. */
    data object ServerError : GqlReply

    /** Not the shape GQL answers with, or a query the server's schema no longer accepts. */
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
    val failed = (reply["errors"] as? JsonArray)?.isNotEmpty() == true
    val data = reply["data"] as? JsonObject
    return when {
        texts.any { text -> authMarkers.any { it in text } } -> GqlReply.AuthExpired
        texts.any { text -> integrityMarkers.any { it in text } } -> GqlReply.IntegrityFailed
        texts.any { text -> schemaMarkers.any { it in text } } -> GqlReply.UnexpectedShape
        data != null -> GqlReply.Data(data, failed)
        failed -> GqlReply.ServerError
        else -> GqlReply.UnexpectedShape
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

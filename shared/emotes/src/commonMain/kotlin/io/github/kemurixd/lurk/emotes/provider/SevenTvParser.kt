package io.github.kemurixd.lurk.emotes.provider

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class SevenTvUser(
    val emoteSetId: String?,
    val emotes: List<Emote>,
)

object SevenTvParser {
    private val log = Logger.withTag("SevenTvParser")
    private const val ZERO_WIDTH_BIT = 1
    private val scaleNames = mapOf("1x" to 1, "2x" to 2, "3x" to 3, "4x" to 4)

    /** An emote set response from `/v3/emote-sets`: reads `emotes[]`. An unreadable body gives an empty list. */
    fun emoteSet(
        json: String,
        scope: EmoteScope,
    ): List<Emote> = emoteSetOrNull(json, scope).orEmpty()

    /** Like [emoteSet], but null when the body is not a JSON object. */
    internal fun emoteSetOrNull(
        json: String,
        scope: EmoteScope,
    ): List<Emote>? = parseObject(json, log)?.let { emotesOf(it, scope) }

    /**
     * `/v3/users/{platform}/{id}`: reads `emote_set.id` and `emote_set.emotes[]`; emotes are channel scope. An
     * unreadable body gives a user with no set.
     */
    fun user(json: String): SevenTvUser = userOrNull(json) ?: SevenTvUser(null, emptyList())

    /** Like [user], but null when the body is not a JSON object. A user without an emote set is still readable. */
    internal fun userOrNull(json: String): SevenTvUser? {
        val root = parseObject(json, log) ?: return null
        val set = root["emote_set"] as? JsonObject
        return SevenTvUser(
            emoteSetId = (set?.get("id") as? JsonPrimitive)?.content,
            emotes = set?.let { emotesOf(it, EmoteScope.CHANNEL) } ?: emptyList(),
        )
    }

    /** Maps one ActiveEmote; returns null (and logs) when it does not decode. */
    fun activeEmote(
        element: JsonElement,
        scope: EmoteScope,
    ): Emote? =
        try {
            map(providerJson.decodeFromJsonElement<ActiveEmote>(element), scope)
        } catch (e: SerializationException) {
            log.w { "Skipping 7TV emote that failed to decode: ${e.message}" }
            null
        } catch (e: IllegalArgumentException) {
            log.w { "Skipping 7TV emote that failed to decode: ${e.message}" }
            null
        }

    private fun emotesOf(
        obj: JsonObject,
        scope: EmoteScope,
    ): List<Emote> = (obj["emotes"] as? JsonArray)?.mapNotNull { activeEmote(it, scope) } ?: emptyList()

    private fun map(
        active: ActiveEmote,
        scope: EmoteScope,
    ): Emote {
        val data = active.data
        val base = "https:" + data.host.url
        val images =
            data.host.files
                .filter { it.format.equals("WEBP", ignoreCase = true) }
                .mapNotNull { file ->
                    scaleNames[file.name.substringBefore(".")]?.let { EmoteImage(it, base + "/" + file.name) }
                }.sortedBy { it.scale }
        return Emote(
            id = active.id,
            name = active.name,
            provider = EmoteProvider.SEVENTV,
            scope = scope,
            images = images,
            animated = data.animated,
            zeroWidth = (active.flags and ZERO_WIDTH_BIT) != 0,
            listed = data.listed,
            contentFlags = data.flags,
        )
    }

    @Serializable
    private class ActiveEmote(
        val id: String,
        val name: String,
        val flags: Int = 0,
        val data: EmoteData,
    )

    @Serializable
    private class EmoteData(
        val flags: Int = 0,
        val listed: Boolean = true,
        val animated: Boolean = false,
        val host: Host,
    )

    @Serializable
    private class Host(
        val url: String,
        val files: List<HostFile> = emptyList(),
    )

    @Serializable
    private class HostFile(
        val name: String,
        val format: String = "WEBP",
    )
}

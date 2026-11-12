package io.github.kemurixd.lurk.emotes.provider

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.FfzEffect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object FfzParser {
    private val log = Logger.withTag("FfzParser")

    /** `/v1/set/global`: only the sets listed in `default_sets`. An unreadable body gives an empty list. */
    fun global(json: String): List<Emote> = globalOrNull(json).orEmpty()

    /** Like [global], but null when the body is not a JSON object with a `sets` object. */
    internal fun globalOrNull(json: String): List<Emote>? {
        val root = parseObject(json, log)
        val sets = setsOf(root) ?: return null
        val defaults =
            (root?.get("default_sets") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty()
        return defaults.flatMap { id -> emotesOf(sets[id], EmoteScope.GLOBAL) }
    }

    /** `/v1/room/id/{id}`: every set in `sets`, channel scope. An unreadable body gives an empty list. */
    fun room(json: String): List<Emote> = roomOrNull(json).orEmpty()

    /** Like [room], but null when the body is not a JSON object with a `sets` object. */
    internal fun roomOrNull(json: String): List<Emote>? =
        setsOf(parseObject(json, log))?.values?.flatMap { emotesOf(it, EmoteScope.CHANNEL) }

    /** The `sets` object of [root], or null (and logged) when the body had none. */
    private fun setsOf(root: JsonObject?): JsonObject? =
        root?.let {
            (it["sets"] as? JsonObject).also { sets ->
                if (sets ==
                    null
                ) {
                    log.w { "FFZ response has no sets" }
                }
            }
        }

    private fun emotesOf(
        set: JsonElement?,
        scope: EmoteScope,
    ): List<Emote> = (set as? JsonObject)?.get("emoticons").decodeEach<Item, Emote>(log) { map(it, scope) }

    private fun map(
        item: Item,
        scope: EmoteScope,
    ): Emote {
        val animatedUrls = item.animated.orEmpty()
        val source = animatedUrls.ifEmpty { item.urls }
        val images =
            source
                .mapNotNull { (key, url) -> key.toIntOrNull()?.let { EmoteImage(it, url) } }
                .sortedBy { it.scale }
        val effects = if (item.modifier) FfzEffect.fromFlags(item.modifierFlags) else emptySet()
        return Emote(
            id = item.id.toString(),
            name = item.name,
            provider = EmoteProvider.FFZ,
            scope = scope,
            images = images,
            animated = animatedUrls.isNotEmpty(),
            modifierEffects = effects,
            hiddenInPicker = item.hidden || FfzEffect.HIDDEN in effects,
        )
    }

    @Serializable
    private class Item(
        val id: Int,
        val name: String,
        val urls: Map<String, String> = emptyMap(),
        val animated: Map<String, String>? = null,
        val hidden: Boolean = false,
        val modifier: Boolean = false,
        @SerialName("modifier_flags") val modifierFlags: Int = 0,
    )
}

package io.github.kemurixd.lurk.emotes.provider

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

object BttvParser {
    private val log = Logger.withTag("BttvParser")

    private val userLists = listOf("channelEmotes", "sharedEmotes")

    /** `/3/cached/emotes/global`: a top-level array. An unreadable body gives an empty list. */
    fun global(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote> = globalOrNull(json, zeroWidthNames).orEmpty()

    /** Like [global], but null when the body is not a JSON array. */
    internal fun globalOrNull(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote>? = parseArray(json, log)?.decodeEach<Item, Emote>(log) { map(it, EmoteScope.GLOBAL, zeroWidthNames) }

    /**
     * `/3/cached/users/twitch/{id}`: `channelEmotes` followed by `sharedEmotes`, channel scope. An unreadable body
     * gives an empty list.
     */
    fun user(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote> = userOrNull(json, zeroWidthNames).orEmpty()

    /** Like [user], but null when the body is not a JSON object holding at least one of the two emote arrays. */
    internal fun userOrNull(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote>? {
        val root = parseObject(json, log) ?: return null
        val readable = userLists.any { root[it] is JsonArray }
        if (!readable) log.w { "BTTV user response has no emote arrays" }
        return if (!readable) {
            null
        } else {
            userLists.flatMap { key ->
                root[key].decodeEach<Item, Emote>(log) { map(it, EmoteScope.CHANNEL, zeroWidthNames) }
            }
        }
    }

    private fun map(
        item: Item,
        scope: EmoteScope,
        zeroWidthNames: Set<String>,
    ): Emote =
        Emote(
            id = item.id,
            name = item.code,
            provider = EmoteProvider.BTTV,
            scope = scope,
            images = (1..3).map { EmoteImage(it, "https://cdn.betterttv.net/emote/${item.id}/${it}x") },
            animated = item.animated,
            zeroWidth = item.code in zeroWidthNames,
        )

    @Serializable
    private class Item(
        val id: String,
        val code: String,
        val animated: Boolean = false,
    )
}

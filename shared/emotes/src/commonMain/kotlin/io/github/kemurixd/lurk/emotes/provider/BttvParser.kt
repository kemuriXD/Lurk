package io.github.kemurixd.lurk.emotes.provider

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import kotlinx.serialization.Serializable

object BttvParser {
    private val log = Logger.withTag("BttvParser")

    /** `/3/cached/emotes/global`: a top-level array. */
    fun global(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote> = parseArray(json, log).decodeEach<Item, Emote>(log) { map(it, EmoteScope.GLOBAL, zeroWidthNames) }

    /** `/3/cached/users/twitch/{id}`: `channelEmotes` followed by `sharedEmotes`, channel scope. */
    fun user(
        json: String,
        zeroWidthNames: Set<String>,
    ): List<Emote> {
        val root = parseObject(json, log) ?: return emptyList()
        return listOf("channelEmotes", "sharedEmotes").flatMap { key ->
            root[key].decodeEach<Item, Emote>(log) { map(it, EmoteScope.CHANNEL, zeroWidthNames) }
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

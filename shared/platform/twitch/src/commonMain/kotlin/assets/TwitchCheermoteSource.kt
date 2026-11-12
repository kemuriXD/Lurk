package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.model.Cheermote
import io.github.kemurixd.lurk.core.model.CheermoteTier
import io.github.kemurixd.lurk.platform.api.CheermoteSource
import io.github.kemurixd.lurk.platform.twitch.discovery.nonBlank
import io.github.kemurixd.lurk.platform.twitch.discovery.number
import io.github.kemurixd.lurk.platform.twitch.discovery.obj
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val CHEERMOTES_OPERATION = "Cheermotes"

internal const val CHEERMOTES_QUERY =
    "query Cheermotes(\$id: ID!) { " +
        "cheerConfig { displayConfig { colors { bits color } } " +
        "groups { templateURL nodes { prefix tiers { bits } } } } " +
        "user(id: \$id) { cheer { cheerGroups { templateURL nodes { prefix tiers { bits } } } } } }"

private const val BACKGROUND = "dark"
private const val ANIMATION = "animated"
private const val EXTENSION = "gif"
private const val SCALE = "2"

/**
 * The cheermotes of Twitch's global config plus those of the channel, signed out. A channel prefix replaces a global
 * one of the same name. Each tier uses the dark, animated, scale-2 image its group's template points at.
 */
class TwitchCheermoteSource(
    private val gql: GqlClient,
) : CheermoteSource {
    override suspend fun load(channelId: String): List<Cheermote> {
        val variables = buildJsonObject { put("id", channelId) }
        return gql.query(CHEERMOTES_OPERATION, CHEERMOTES_QUERY, variables, null, ::decodeCheermotes).valueOrThrow()
    }
}

internal fun decodeCheermotes(data: JsonObject): List<Cheermote> {
    val config = data.obj("cheerConfig")
    val colors = config?.obj("displayConfig")?.let(::colorsByBits).orEmpty()
    val global = config?.objects("groups").orEmpty()
    val channel =
        data
            .obj("user")
            ?.obj("cheer")
            ?.objects("cheerGroups")
            .orEmpty()
    val merged = LinkedHashMap<String, Cheermote>()
    (global + channel).flatMap { cheermotesOf(it, colors) }.forEach { merged[it.prefix] = it }
    return merged.values.toList()
}

private fun colorsByBits(display: JsonObject): Map<Long, String> =
    display
        .objects("colors")
        .mapNotNull { entry ->
            val bits = entry.number("bits")
            val color = entry.nonBlank("color")
            if (bits == null || color == null) null else bits to color
        }.toMap()

private fun cheermotesOf(
    group: JsonObject,
    colors: Map<Long, String>,
): List<Cheermote> {
    val template = group.nonBlank("templateURL") ?: return emptyList()
    return group.objects("nodes").mapNotNull { node ->
        val prefix = node.nonBlank("prefix") ?: return@mapNotNull null
        val tiers =
            node
                .objects("tiers")
                .mapNotNull { it.number("bits") }
                .distinct()
                .sorted()
                .map { bits -> CheermoteTier(bits.toInt(), colors[bits], imageUrl(template, prefix, bits)) }
        if (tiers.isEmpty()) null else Cheermote(prefix, tiers)
    }
}

private fun imageUrl(
    template: String,
    prefix: String,
    bits: Long,
): String =
    template
        .replace("PREFIX", prefix.lowercase())
        .replace("BACKGROUND", BACKGROUND)
        .replace("ANIMATION", ANIMATION)
        .replace("TIER", bits.toString())
        .replace("SCALE", SCALE)
        .replace("EXTENSION", EXTENSION)

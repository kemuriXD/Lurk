package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.platform.api.BadgeSource
import io.github.kemurixd.lurk.platform.twitch.discovery.nonBlank
import io.github.kemurixd.lurk.platform.twitch.discovery.obj
import io.github.kemurixd.lurk.platform.twitch.discovery.text
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val BADGES_OPERATION = "Badges"

internal const val BADGES_QUERY =
    "query Badges(\$id: ID!) { " +
        "badges { setID version title imageURL(size: QUADRUPLE) } " +
        "user(id: \$id) { broadcastBadges { setID version title imageURL(size: QUADRUPLE) } } }"

/** Twitch's global badges with the channel's own on top, in one signed-out request. */
class TwitchBadgeSource(
    private val gql: GqlClient,
) : BadgeSource {
    override suspend fun load(channelId: String): Map<String, Badge> {
        val variables = buildJsonObject { put("id", channelId) }
        return gql.query(BADGES_OPERATION, BADGES_QUERY, variables, null, ::decodeBadges).valueOrThrow()
    }
}

internal fun decodeBadges(data: JsonObject): Map<String, Badge> {
    val global = data.objects("badges")
    val channel = data.obj("user")?.objects("broadcastBadges").orEmpty()
    return (global + channel).mapNotNull(::badgeOf).associateBy { "${it.setId}/${it.version}" }
}

private fun badgeOf(fields: JsonObject): Badge? {
    val setId = fields.nonBlank("setID")
    val version = fields.nonBlank("version")
    return if (setId == null || version == null) {
        null
    } else {
        Badge(setId, version, fields.text("title"), fields.nonBlank("imageURL"))
    }
}

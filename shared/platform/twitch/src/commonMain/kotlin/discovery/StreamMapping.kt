package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal const val PAGE_SIZE = 30

/** The fields of a live stream that every discovery query asks for. */
internal const val STREAM_FIELDS =
    "id title viewersCount createdAt previewImageURL " +
        "broadcaster { id login displayName profileImageURL(width: 70) } " +
        "game { id name boxArtURL }"

private const val THUMBNAIL_SIZE = "440x248"
private const val BOX_ART_SIZE = "285x380"
private const val SIZE_TEMPLATE = "{width}x{height}"

/** A game as a category, or null when it has no ID or name. [viewers] is only known for the game list. */
internal fun categoryOf(
    game: JsonObject?,
    viewers: Long? = null,
): Category? {
    val id = game?.nonBlank("id")
    val name = game?.nonBlank("name")
    return if (game == null || id == null || name == null) {
        null
    } else {
        Category(Platform.TWITCH, id, name, game.text("boxArtURL")?.sized(BOX_ART_SIZE), viewers)
    }
}

/**
 * A stream node as a [Stream]. The broadcaster is the node's own, or [fallback] for a stream read through its
 * user. Null when there is no broadcaster to name.
 */
internal fun streamOf(
    node: JsonObject,
    fallback: JsonObject? = null,
): Stream? {
    val broadcaster = node.obj("broadcaster") ?: fallback
    val login = broadcaster?.let(::loginOrNull)
    val id = broadcaster?.nonBlank("id")
    return if (broadcaster == null || login == null || id == null) {
        null
    } else {
        Stream(
            channel = ChannelRef(Platform.TWITCH, login),
            channelId = id,
            displayName = broadcaster.text("displayName") ?: login,
            title = node.text("title").orEmpty(),
            category = categoryOf(node.obj("game")),
            viewers = node.number("viewersCount") ?: 0,
            startedAt = instantOrNull(node.text("createdAt")),
            thumbnailUrl = node.text("previewImageURL")?.sized(THUMBNAIL_SIZE),
            avatarUrl = broadcaster.text("profileImageURL"),
        )
    }
}

/**
 * Reads a connection's edges into a page. Items [map] cannot read are dropped. The cursor is the last edge's
 * (even when that edge was dropped) and only when Twitch says there is another page.
 */
internal fun <T : Any> pageOf(
    connection: JsonObject,
    map: (JsonObject) -> T?,
): Page<T> {
    val edges = edgesOf(connection)
    val hasNext = (connection.obj("pageInfo")?.get("hasNextPage") as? JsonPrimitive)?.booleanOrNull == true
    val cursor = if (hasNext) edges.lastOrNull()?.text("cursor") else null
    return Page(edges.mapNotNull { edge -> edge.obj("node")?.let(map) }, cursor)
}

internal fun edgesOf(connection: JsonObject): List<JsonObject> =
    ((connection["edges"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }

private fun String.sized(size: String): String = replace(SIZE_TEMPLATE, size)

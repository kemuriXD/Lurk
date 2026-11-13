package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val GAME_STREAMS_OPERATION = "GameStreams"

internal const val GAME_STREAMS_QUERY =
    "query GameStreams(\$id: ID!, \$first: Int!, \$after: Cursor, \$options: GameStreamOptions) { " +
        "game(id: \$id) { streams(first: \$first, after: \$after, options: \$options) { " +
        "edges { cursor node { $STREAM_FIELDS } } pageInfo { hasNextPage } } } }"

/**
 * The directory's `StreamSort` value for each sort Twitch offers. Probed signed out on 2026-11-13: all four answer
 * without a token, and any other value is a GraphQL error.
 */
internal fun CategorySort.streamSort(): String? =
    when (this) {
        CategorySort.RECOMMENDED -> "RELEVANCE"
        CategorySort.VIEWERS_HIGH -> "VIEWER_COUNT"
        CategorySort.VIEWERS_LOW -> "VIEWER_COUNT_ASC"
        CategorySort.RECENT -> "RECENT"
        CategorySort.FEATURED -> null
    }

/**
 * One page of the live streams in a category, in [sort] order. An ID Twitch does not know is `NotFound`, and a sort
 * Twitch doesn't have is `Unsupported` without a request.
 */
internal suspend fun GqlClient.liveInCategory(
    sessions: SessionRepository,
    id: String,
    cursor: String?,
    sort: CategorySort,
): Outcome<Page<Stream>> {
    val streamSort = sort.streamSort() ?: return Outcome.Failure(LurkError.Unsupported)
    return sessions.twitchCall { token ->
        val variables =
            buildJsonObject {
                put("id", id)
                pageVariables(cursor).forEach { (key, value) -> put(key, value) }
                put("options", buildJsonObject { put("sort", streamSort) })
            }
        query(GAME_STREAMS_OPERATION, GAME_STREAMS_QUERY, variables, token, ::decodeGameStreams)
    }
}

internal fun decodeGameStreams(data: JsonObject): Page<Stream> {
    val game = data.getValue("game")
    if (game is JsonNull) throw LurkException(LurkError.NotFound)
    return pageOf((game as JsonObject).requireObj("streams")) { streamOf(it) }
}

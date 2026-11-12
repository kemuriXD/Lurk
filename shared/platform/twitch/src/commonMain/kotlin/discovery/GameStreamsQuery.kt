package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val GAME_STREAMS_OPERATION = "GameStreams"

internal const val GAME_STREAMS_QUERY =
    "query GameStreams(\$id: ID!, \$first: Int!, \$after: Cursor) { " +
        "game(id: \$id) { streams(first: \$first, after: \$after) { " +
        "edges { cursor node { $STREAM_FIELDS } } pageInfo { hasNextPage } } } }"

/** One page of the live streams in a category. An ID Twitch does not know is `NotFound`. */
internal suspend fun GqlClient.liveInCategory(
    sessions: SessionRepository,
    id: String,
    cursor: String?,
): Outcome<Page<Stream>> =
    sessions.authed(Platform.TWITCH, anonymousFallback = true) { token ->
        val variables =
            buildJsonObject {
                put("id", id)
                pageVariables(cursor).forEach { (key, value) -> put(key, value) }
            }
        query(GAME_STREAMS_OPERATION, GAME_STREAMS_QUERY, variables, token, ::decodeGameStreams)
    }

internal fun decodeGameStreams(data: JsonObject): Page<Stream> {
    val game = data.getValue("game")
    if (game is JsonNull) throw LurkException(LurkError.NotFound)
    return pageOf((game as JsonObject).requireObj("streams")) { streamOf(it) }
}

package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TOP_STREAMS_OPERATION = "TopStreams"

internal const val TOP_STREAMS_QUERY =
    "query TopStreams(\$first: Int!, \$after: Cursor) { " +
        "streams(first: \$first, after: \$after) { " +
        "edges { cursor node { $STREAM_FIELDS } } pageInfo { hasNextPage } } }"

/** One page of the live streams with the most viewers. [cursor] is the previous page's `nextCursor`. */
internal suspend fun GqlClient.topLive(
    sessions: SessionRepository,
    cursor: String?,
): Outcome<Page<Stream>> =
    sessions.twitchCall { token ->
        query(TOP_STREAMS_OPERATION, TOP_STREAMS_QUERY, pageVariables(cursor), token, ::decodeTopStreams)
    }

/** `first` and, past the first page, `after`. */
internal fun pageVariables(cursor: String?): JsonObject =
    buildJsonObject {
        put("first", PAGE_SIZE)
        if (cursor != null) put("after", cursor)
    }

internal fun decodeTopStreams(data: JsonObject): Page<Stream> = pageOf(data.requireObj("streams")) { streamOf(it) }

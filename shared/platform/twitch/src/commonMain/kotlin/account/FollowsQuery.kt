package io.github.kemurixd.lurk.platform.twitch.account

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.twitch.discovery.STREAM_FIELDS
import io.github.kemurixd.lurk.platform.twitch.discovery.channelOf
import io.github.kemurixd.lurk.platform.twitch.discovery.obj
import io.github.kemurixd.lurk.platform.twitch.discovery.pageOf
import io.github.kemurixd.lurk.platform.twitch.discovery.requireObj
import io.github.kemurixd.lurk.platform.twitch.discovery.streamOf
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val FOLLOWS_OPERATION = "Follows"

internal const val FOLLOWS_QUERY =
    "query Follows(\$first: Int!, \$after: Cursor) { currentUser { " +
        "follows(first: \$first, after: \$after) { " +
        "edges { cursor node { id login displayName profileImageURL(width: 70) stream { $STREAM_FIELDS } } } " +
        "pageInfo { hasNextPage } } } }"

internal const val FOLLOWS_PAGE_SIZE = 100
internal const val FOLLOWS_MAX_PAGES = 20

private sealed interface Entry {
    class Live(
        val stream: Stream,
    ) : Entry

    class Offline(
        val channel: Channel,
    ) : Entry
}

/**
 * Everything the signed-in user follows: live ones as streams by viewers, the rest as channels A to Z. Reads at most
 * [FOLLOWS_MAX_PAGES] pages. Signed out is `NotSignedIn`; there is no anonymous answer.
 */
internal suspend fun GqlClient.followed(sessions: SessionRepository): Outcome<Followed> =
    sessions.authed(Platform.TWITCH, anonymousFallback = false) { token ->
        if (token == null) return@authed Outcome.Failure(LurkError.NotSignedIn)
        val entries = mutableListOf<Entry>()
        var cursor: String? = null
        var pages = 0
        do {
            val result = query(FOLLOWS_OPERATION, FOLLOWS_QUERY, followsVariables(cursor), token, ::decodeFollows)
            when (result) {
                is Outcome.Failure -> {
                    return@authed result
                }

                is Outcome.Success -> {
                    entries += result.value.items
                    cursor = result.value.nextCursor
                }
            }
            pages++
        } while (cursor != null && pages < FOLLOWS_MAX_PAGES)
        Outcome.Success(split(entries))
    }

private fun followsVariables(cursor: String?): JsonObject =
    buildJsonObject {
        put("first", FOLLOWS_PAGE_SIZE)
        if (cursor != null) put("after", cursor)
    }

/** A null `currentUser` means the token no longer names anyone. */
private fun decodeFollows(data: JsonObject): Page<Entry> {
    val user = data["currentUser"]
    if (user == null || user is JsonNull) throw LurkException(LurkError.AuthExpired)
    return pageOf((user as JsonObject).requireObj("follows"), ::entryOf)
}

private fun entryOf(node: JsonObject): Entry? {
    val channel = channelOf(node) ?: return null
    val stream = node.obj("stream")?.let { streamOf(it, fallback = node) }
    return if (stream != null) Entry.Live(stream) else Entry.Offline(channel)
}

private fun split(entries: List<Entry>): Followed =
    Followed(
        live = entries.filterIsInstance<Entry.Live>().map { it.stream }.sortedByDescending { it.viewers },
        offline =
            entries
                .filterIsInstance<Entry.Offline>()
                .map { it.channel }
                .sortedBy { it.displayName.lowercase() },
    )

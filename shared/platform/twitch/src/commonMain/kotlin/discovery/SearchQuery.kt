package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val SEARCH_OPERATION = "SearchFor"

internal const val SEARCH_QUERY =
    "query SearchFor(\$query: String!) { " +
        "searchFor(userQuery: \$query, platform: \"web\") { " +
        "channels { edges { item { ... on User { " +
        "id login displayName description profileImageURL(width: 70) followers { totalCount } " +
        "stream { $STREAM_FIELDS } } } } } " +
        "games { edges { item { ... on Game { id name boxArtURL viewersCount } } } } } }"

/** Channels and categories matching [query]. Live channels come before offline ones. */
internal suspend fun GqlClient.search(
    sessions: SessionRepository,
    query: String,
): Outcome<SearchResults> =
    sessions.twitchCall { token ->
        query(SEARCH_OPERATION, SEARCH_QUERY, buildJsonObject { put("query", query) }, token, ::decodeSearch)
    }

internal fun decodeSearch(data: JsonObject): SearchResults {
    val results = data.requireObj("searchFor")
    val channels = items(results, "channels").mapNotNull(::channelOf)
    val categories = items(results, "games").mapNotNull { categoryOf(it, it.number("viewersCount")) }
    // sortedByDescending is stable, so Twitch's order holds within the live and offline groups.
    return SearchResults(channels.sortedByDescending { it.live != null }, categories)
}

private fun items(
    results: JsonObject,
    key: String,
): List<JsonObject> =
    results
        .obj(key)
        ?.let(::edgesOf)
        .orEmpty()
        .mapNotNull { it.obj("item") }

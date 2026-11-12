package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject

internal const val GAMES_OPERATION = "TopGames"

internal const val GAMES_QUERY =
    "query TopGames(\$first: Int!, \$after: Cursor) { " +
        "games(first: \$first, after: \$after) { " +
        "edges { cursor node { id name boxArtURL viewersCount } } pageInfo { hasNextPage } } }"

/** One page of categories, in the order Twitch gives them: most viewers first. */
internal suspend fun GqlClient.categories(
    sessions: SessionRepository,
    cursor: String?,
): Outcome<Page<Category>> =
    sessions.twitchCall { token ->
        query(GAMES_OPERATION, GAMES_QUERY, pageVariables(cursor), token, ::decodeGames)
    }

internal fun decodeGames(data: JsonObject): Page<Category> =
    pageOf(data.requireObj("games")) { categoryOf(it, it.number("viewersCount")) }

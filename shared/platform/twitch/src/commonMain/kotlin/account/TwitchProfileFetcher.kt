package io.github.kemurixd.lurk.platform.twitch.account

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.twitch.discovery.loginOrNull
import io.github.kemurixd.lurk.platform.twitch.discovery.nonBlank
import io.github.kemurixd.lurk.platform.twitch.discovery.text
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

private const val PROFILE_OPERATION = "CurrentUser"

private const val PROFILE_QUERY =
    "query CurrentUser { currentUser { id login displayName profileImageURL(width: 70) } }"

/** Looks up the account behind a Twitch token. A token Twitch no longer knows has no `currentUser`. */
class TwitchProfileFetcher(
    private val gql: GqlClient,
) : ProfileFetcher {
    override val platform = Platform.TWITCH

    override suspend fun profile(token: String): Outcome<Account> =
        gql.query(PROFILE_OPERATION, PROFILE_QUERY, buildJsonObject { }, token, ::decodeAccount)
}

private fun decodeAccount(data: JsonObject): Account {
    val user = data["currentUser"]
    if (user == null || user is JsonNull) throw LurkException(LurkError.AuthExpired)
    val fields = user as JsonObject
    val id = fields.nonBlank("id")
    val login = loginOrNull(fields)
    check(id != null && login != null) { "currentUser has no id or login" }
    return Account(Platform.TWITCH, id, login, fields.text("displayName") ?: login, fields.text("profileImageURL"))
}

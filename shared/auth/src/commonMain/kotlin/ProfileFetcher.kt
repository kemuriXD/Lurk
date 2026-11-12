package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform

/** Looks up the account behind a session token. A rejected token must come back as `LurkError.AuthExpired`. */
interface ProfileFetcher {
    val platform: Platform

    suspend fun profile(token: String): Outcome<Account>
}

package io.github.kemurixd.lurk.platform.kick

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.TestScope

internal const val RIG_TOKEN = "rig-token"

private class MemoryStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() = values.clear()
}

private class RigProfiles : ProfileFetcher {
    override val platform = Platform.KICK

    override suspend fun profile(token: String): Outcome<Account> =
        Outcome.Success(Account(Platform.KICK, "1000099", "viewer1", "Viewer One", null))
}

/** A session repository for the tests. Signed out unless [signedIn], which signs in with [RIG_TOKEN]. */
internal suspend fun TestScope.rigSessions(signedIn: Boolean = false): SessionRepository {
    val sessions = SessionRepository(MemoryStore(), listOf(RigProfiles()), backgroundScope)
    if (signedIn) sessions.signIn(Platform.KICK, RIG_TOKEN)
    return sessions
}

internal fun HttpRequestData.queryOf(name: String): String? = url.parameters[name]

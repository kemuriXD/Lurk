package io.github.kemurixd.lurk.feature.testing

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore

/** A [SecureStore] kept in a map, for building a real `SessionRepository` in tests. */
class InMemorySecureStore : SecureStore {
    val values = mutableMapOf<String, String>()

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

/** A [ProfileFetcher] that answers every token with [result]. */
class FakeProfileFetcher(
    override val platform: Platform,
    var result: Outcome<Account> = Outcome.Success(testAccount(platform)),
) : ProfileFetcher {
    var calls = 0
        private set

    override suspend fun profile(token: String): Outcome<Account> {
        calls++
        return result
    }
}

/** A stand-in account. */
fun testAccount(
    platform: Platform,
    login: String = "streamer1",
    displayName: String = "Streamer One",
    userId: String = "1000001",
): Account = Account(platform, userId, login, displayName, null)

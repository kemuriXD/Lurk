package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A platform's token and account in [SecureStore], under `<platform.id>.token` and `<platform.id>.account`. */
internal class SessionVault(
    private val secure: SecureStore,
) {
    suspend fun token(platform: Platform): String? = secure.get(tokenKey(platform))

    /** The cached account, or null when it is missing or cannot be read. */
    suspend fun account(platform: Platform): Account? {
        val raw = secure.get(accountKey(platform)) ?: return null
        return try {
            json.decodeFromString<Account>(raw)
        } catch (_: SerializationException) {
            null
        }
    }

    suspend fun putToken(
        platform: Platform,
        token: String,
    ) = secure.put(tokenKey(platform), token)

    suspend fun putAccount(
        platform: Platform,
        account: Account,
    ) = secure.put(accountKey(platform), json.encodeToString(account))

    suspend fun remove(platform: Platform) {
        secure.remove(tokenKey(platform))
        secure.remove(accountKey(platform))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun tokenKey(platform: Platform) = "${platform.id}.token"

        fun accountKey(platform: Platform) = "${platform.id}.account"
    }
}

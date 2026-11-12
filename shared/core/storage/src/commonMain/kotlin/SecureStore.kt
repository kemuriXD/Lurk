package io.github.kemurixd.lurk.core.storage

/** Small secrets (session tokens and the like) encrypted at rest by the platform. */
interface SecureStore {
    suspend fun get(key: String): String?

    suspend fun put(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)

    suspend fun clear()
}

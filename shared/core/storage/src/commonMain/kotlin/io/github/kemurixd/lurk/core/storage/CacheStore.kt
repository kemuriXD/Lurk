package io.github.kemurixd.lurk.core.storage

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** A cached response body and when it was fetched. */
data class CachedValue(
    val value: String,
    val fetchedAt: Instant,
)

/** Key-value cache in SQLite. Fetch times are stored as epoch milliseconds. */
class CacheStore(
    db: LurkDatabase,
    private val dispatchers: DispatcherProvider,
) {
    private val queries = db.cacheEntryQueries

    suspend fun get(key: String): CachedValue? =
        withContext(dispatchers.io) {
            queries
                .get(key) { value, fetchedAt -> CachedValue(value, Instant.fromEpochMilliseconds(fetchedAt)) }
                .executeAsOneOrNull()
        }

    suspend fun put(
        key: String,
        value: String,
        at: Instant,
    ) = withContext(dispatchers.io) {
        queries.upsert(key, value, at.toEpochMilliseconds())
    }

    suspend fun clear() =
        withContext(dispatchers.io) {
            queries.deleteAll()
        }
}

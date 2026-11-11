package io.github.kemurixd.lurk.core.config

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Holds the current [RemoteConfig]. Starts from the copy bundled at build time, switches to the cached
 * copy on [load], and fetches a fresh one at most once a day. A config that fails to fetch, decode or
 * validate is never used, so the app keeps running on the last good one.
 */
class RemoteConfigRepository(
    private val http: HttpClient,
    private val cache: CacheStore,
    private val clock: Clock,
    private val url: String = DEFAULT_URL,
) {
    private val bundled: RemoteConfig = json.decodeFromString(BUNDLED_REMOTE_CONFIG)
    private val state = MutableStateFlow(bundled)
    val config: StateFlow<RemoteConfig> = state.asStateFlow()

    /** Uses the cached config if it still decodes and validates, otherwise the bundled one. */
    suspend fun load() {
        val cached = cache.get(CACHE_KEY)?.value?.let(::decodeValid)
        state.value = cached ?: bundled
    }

    /** Fetches, validates and caches a new config unless the last fetch was under 24 hours ago. */
    suspend fun refreshIfDue() {
        val now = clock.now()
        val fetchedAt = cache.get(CACHE_KEY)?.fetchedAt
        if (fetchedAt != null && fetchedAt <= now && now - fetchedAt < REFRESH_INTERVAL) return

        when (val fetched = outcomeOf { http.get(url).bodyAsText() }) {
            is Outcome.Failure -> {
                log.w { "Remote config fetch failed: ${fetched.error}" }
            }

            is Outcome.Success -> {
                val config = decodeValid(fetched.value) ?: return
                cache.put(CACHE_KEY, fetched.value, now)
                state.value = config
            }
        }
    }

    private fun decodeValid(text: String): RemoteConfig? {
        val config =
            try {
                json.decodeFromString<RemoteConfig>(text)
            } catch (e: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException, as are the decoder's input errors.
                log.w { "Remote config does not decode: ${e.message?.take(MAX_LOGGED_CHARS)}" }
                null
            }
        val errors = config?.let(RemoteConfigValidator::errors).orEmpty()
        if (errors.isNotEmpty()) log.w { "Remote config rejected: ${errors.joinToString("; ")}" }
        return config?.takeIf { errors.isEmpty() }
    }

    companion object {
        const val DEFAULT_URL = "https://raw.githubusercontent.com/kemuriXD/Lurk/main/config/remote.json"
        private const val CACHE_KEY = "remote_config"
        private const val MAX_LOGGED_CHARS = 200
        private val REFRESH_INTERVAL = 24.hours
        private val json = Json { ignoreUnknownKeys = true }
        private val log = Logger.withTag("RemoteConfig")
    }
}

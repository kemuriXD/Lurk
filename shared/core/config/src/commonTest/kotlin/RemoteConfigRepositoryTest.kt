package io.github.kemurixd.lurk.core.config

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RemoteConfigRepositoryTest {
    private class FakeClock(
        var now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val clock = FakeClock(start)
    private val bundled = Json.decodeFromString<RemoteConfig>(BUNDLED_REMOTE_CONFIG)

    private fun TestScope.cacheStore() =
        CacheStore(LurkDatabase(inMemoryDriver()), TestDispatchers(StandardTestDispatcher(testScheduler)))

    private fun repository(
        cache: CacheStore,
        engine: MockEngine,
    ) = RemoteConfigRepository(createHttpClient(engine), cache, clock, url = URL)

    /** Serves [responses] in order, one per request. With none, any request fails the test. */
    private fun engineOf(vararg responses: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                responses.forEach { addHandler(it) }
                if (responses.isEmpty()) addHandler { fail("unexpected request to ${it.url}") }
            },
        )

    private fun json(config: RemoteConfig) = Json.encodeToString(RemoteConfig.serializer(), config)

    @Test
    fun loadsBundledWhenCacheEmpty() =
        runTest {
            val repo = repository(cacheStore(), engineOf())
            repo.load()
            assertEquals(bundled, repo.config.value)
        }

    @Test
    fun loadsValidCachedCopy() =
        runTest {
            val cache = cacheStore()
            val cached = validConfig()
            cache.put(CACHE_KEY, json(cached), start)
            val repo = repository(cache, engineOf())
            repo.load()
            assertEquals(cached, repo.config.value)
        }

    @Test
    fun refreshStoresValidFetch() =
        runTest {
            val cache = cacheStore()
            val fetched = bundled.copy(killSwitches = mapOf("kick_chat" to true))
            val engine = engineOf({ respond(json(fetched)) })
            val repo = repository(cache, engine)
            repo.load()
            repo.refreshIfDue()
            assertEquals(fetched, repo.config.value)
            val stored = assertNotNull(cache.get(CACHE_KEY))
            assertEquals(fetched, Json.decodeFromString<RemoteConfig>(stored.value))
            assertEquals(start, stored.fetchedAt)
            assertEquals(
                URL,
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun notDueWithin24h() =
        runTest {
            val engine = engineOf({ respond(json(bundled)) }, { respond(json(bundled)) })
            val repo = repository(cacheStore(), engine)
            repo.load()
            repo.refreshIfDue()
            assertEquals(1, engine.requestHistory.size)
            clock.now = start + 23.hours
            repo.refreshIfDue()
            assertEquals(1, engine.requestHistory.size)
            clock.now = start + 25.hours
            repo.refreshIfDue()
            assertEquals(2, engine.requestHistory.size)
        }

    @Test
    fun futureTimestampTreatedAsDue() =
        runTest {
            val cache = cacheStore()
            cache.put(CACHE_KEY, json(bundled), start + 2.days)
            val engine = engineOf({ respond(json(bundled)) })
            val repo = repository(cache, engine)
            repo.load()
            repo.refreshIfDue()
            assertEquals(1, engine.requestHistory.size)
            assertEquals(start, assertNotNull(cache.get(CACHE_KEY)).fetchedAt)
        }

    @Test
    fun invalidFetchKeepsPrevious() =
        runTest {
            val cache = cacheStore()
            val engine =
                engineOf(
                    { respond("<html>") },
                    { respond("""{"schemaVersion":2}""") },
                    { respond("oops", HttpStatusCode.InternalServerError) },
                )
            val repo = repository(cache, engine)
            repo.load()
            repeat(3) {
                repo.refreshIfDue()
                assertEquals(bundled, repo.config.value)
            }
            assertEquals(3, engine.requestHistory.size)
            assertNull(cache.get(CACHE_KEY))
        }

    @Test
    fun newerSchemaIgnored() =
        runTest {
            val cache = cacheStore()
            val engine = engineOf({ respond(json(bundled.copy(schemaVersion = 3))) })
            val repo = repository(cache, engine)
            repo.load()
            repo.refreshIfDue()
            assertEquals(bundled, repo.config.value)
            assertNull(cache.get(CACHE_KEY))
        }

    @Test
    fun invalidCacheFallsBackToBundled() =
        runTest {
            val cache = cacheStore()
            cache.put(CACHE_KEY, "{", start)
            val repo = repository(cache, engineOf())
            repo.load()
            assertEquals(bundled, repo.config.value)
        }

    @Test
    fun cachedCopyFailingValidationFallsBackToBundled() =
        runTest {
            val cache = cacheStore()
            cache.put(CACHE_KEY, json(validConfig().copy(schemaVersion = 1)), start)
            val repo = repository(cache, engineOf())
            repo.load()
            assertEquals(bundled, repo.config.value)
        }

    private companion object {
        const val URL = "https://config.example.test/remote.json"
        const val CACHE_KEY = "remote_config"
    }
}

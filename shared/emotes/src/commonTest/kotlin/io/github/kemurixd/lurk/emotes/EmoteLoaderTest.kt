package io.github.kemurixd.lurk.emotes

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.github.kemurixd.lurk.emotes.provider.BttvParser
import io.github.kemurixd.lurk.emotes.provider.FfzParser
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EmoteLoaderTest {
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
    private val twitch = EmoteChannel(Platform.TWITCH, "1000500")
    private val kick = EmoteChannel(Platform.KICK, "2000600")
    private val all = EmoteProvider.entries.toSet()
    private val registry = EmoteRegistry()
    private val lenient = Json { ignoreUnknownKeys = true }
    private val config =
        MutableStateFlow(
            lenient.decodeFromString<RemoteConfig>(
                SystemFileSystem.source(Path("../../config/remote.json")).buffered().use { it.readString() },
            ),
        )
    private val listSerializer = ListSerializer(Emote.serializer())

    private fun TestScope.cacheStore() =
        CacheStore(LurkDatabase(inMemoryDriver()), TestDispatchers(StandardTestDispatcher(testScheduler)))

    private fun body(url: String): String =
        when {
            url.endsWith("/emote-sets/global") -> fixture("seventv/global.json")
            url.endsWith("/cached/emotes/global") -> fixture("bttv/global.json")
            url.contains("/cached/users/twitch/") -> fixture("bttv/user.json")
            url.contains("/users/twitch/") || url.contains("/users/kick/") -> fixture("seventv/user-twitch.json")
            url.endsWith("/set/global") -> fixture("ffz/global.json")
            url.contains("/room/id/") -> fixture("ffz/room.json")
            else -> error("unexpected url $url")
        }

    private fun TestScope.engine(
        bodyOverride: (HttpRequestData) -> String? = { null },
        override: (HttpRequestData) -> HttpStatusCode? = { null },
    ) = MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val status = override(request)
                when {
                    status != null -> respond("", status)
                    else -> respond(bodyOverride(request) ?: body(request.url.toString()))
                }
            }
        },
    )

    private fun loader(
        cache: CacheStore,
        engine: MockEngine,
        native: NativeEmoteSource? = null,
    ) = EmoteLoader(
        EmoteApi(createHttpClient(engine)),
        cache,
        registry,
        config,
        clock,
        native,
        Backoff(base = 2.seconds, max = 60.seconds, random = Random(1)),
    )

    private fun count(
        provider: EmoteProvider,
        scope: EmoteScope,
    ) = registry.snapshot.value.sources[EmoteKey(provider, scope)]
        ?.size ?: 0

    private fun emote(
        name: String,
        provider: EmoteProvider,
        scope: EmoteScope,
    ) = Emote(name, name, provider, scope, listOf(EmoteImage(1, "https://example.test/$name.png")))

    private fun outcomeOf(
        results: List<SourceResult>,
        source: EmoteSource,
    ) = results.single { it.source == source }.outcome

    private val bttvUserCount = BttvParser.user(fixture("bttv/user.json"), emptySet()).size
    private val ffzRoomCount = FfzParser.room(fixture("ffz/room.json")).size

    private fun HttpRequestData.has(part: String) = part in url.toString()

    @Test
    fun providerFailureIsolated() =
        runTest {
            val e = engine { if (it.has("/cached/users/twitch/")) HttpStatusCode.InternalServerError else null }
            val results = loader(cacheStore(), e).load(twitch, all, backgroundScope)
            assertEquals(6, count(EmoteProvider.SEVENTV, EmoteScope.CHANNEL))
            assertEquals(ffzRoomCount, count(EmoteProvider.FFZ, EmoteScope.CHANNEL))
            assertEquals(0, count(EmoteProvider.BTTV, EmoteScope.CHANNEL))
            assertEquals(6, count(EmoteProvider.BTTV, EmoteScope.GLOBAL))
            assertEquals(Outcome.Failure(LurkError.Http(500)), outcomeOf(results, EmoteSource.BTTV_CHANNEL))
            assertIs<Outcome.Success<Int>>(outcomeOf(results, EmoteSource.FFZ_CHANNEL))
        }

    @Test
    fun missingUserIsEmpty() =
        runTest {
            val e = engine { if (it.has("/users/twitch/")) HttpStatusCode.NotFound else null }
            val results = loader(cacheStore(), e).load(twitch, all, backgroundScope)
            assertEquals(Outcome.Success(0), outcomeOf(results, EmoteSource.SEVENTV_CHANNEL))
            val requests = e.requestHistory.size
            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals(requests, e.requestHistory.size)
        }

    @Test
    fun kickSkipsTwitchOnly() =
        runTest {
            val e = engine()
            val results = loader(cacheStore(), e).load(kick, all, backgroundScope)
            assertEquals(setOf("7tv.io"), e.requestHistory.map { it.url.host }.toSet())
            assertEquals(
                setOf(EmoteSource.SEVENTV_GLOBAL, EmoteSource.SEVENTV_CHANNEL),
                results.map { it.source }.toSet(),
            )
        }

    @Test
    fun disabledProvidersAreSkipped() =
        runTest {
            val e = engine()
            loader(cacheStore(), e).load(twitch, setOf(EmoteProvider.FFZ), backgroundScope)
            assertEquals(setOf("api.frankerfacez.com"), e.requestHistory.map { it.url.host }.toSet())
        }

    @Test
    fun nativeRunsOnlyWhenSupplied() =
        runTest {
            val supplied = NativeEmoteSource { listOf(emote("Kappa", EmoteProvider.TWITCH, EmoteScope.CHANNEL)) }
            val with = loader(cacheStore(), engine(), supplied).load(twitch, all, backgroundScope)
            assertEquals(Outcome.Success(1), outcomeOf(with, EmoteSource.NATIVE))
            assertEquals(1, count(EmoteProvider.TWITCH, EmoteScope.CHANNEL))
            val without = loader(cacheStore(), engine()).load(twitch, all, backgroundScope)
            assertNull(without.firstOrNull { it.source == EmoteSource.NATIVE })
        }

    @Test
    fun nativeOnKickFillsKickProvider() =
        runTest {
            val supplied = NativeEmoteSource { listOf(emote("KEKW", EmoteProvider.KICK, EmoteScope.CHANNEL)) }
            loader(cacheStore(), engine(), supplied).load(kick, all, backgroundScope)
            assertEquals(1, count(EmoteProvider.KICK, EmoteScope.CHANNEL))
        }

    @Test
    fun sevenTvSetIdIsExposed() =
        runTest {
            val l = loader(cacheStore(), engine())
            assertNull(l.sevenTvSetId.value)
            l.load(twitch, all, backgroundScope)
            assertEquals("01AA0000000000000000000011", l.sevenTvSetId.value)
        }

    @Test
    fun freshCacheSkipsRequest() =
        runTest {
            val cache = cacheStore()
            loader(cache, engine()).load(twitch, all, backgroundScope)
            clock.now = start + 29.minutes
            val second = engine()
            val l = loader(cache, second)
            val results = l.load(twitch, all, backgroundScope)
            assertEquals(0, second.requestHistory.size)
            assertEquals(6, count(EmoteProvider.SEVENTV, EmoteScope.CHANNEL))
            assertIs<Outcome.Success<Int>>(outcomeOf(results, EmoteSource.BTTV_CHANNEL))
            assertEquals("01AA0000000000000000000011", l.sevenTvSetId.value)
        }

    @Test
    fun staleCacheRefetches() =
        runTest {
            val cache = cacheStore()
            loader(cache, engine()).load(twitch, all, backgroundScope)
            clock.now = start + 31.minutes
            val second = engine()
            loader(cache, second).load(twitch, all, backgroundScope)
            assertEquals(6, second.requestHistory.size)
        }

    @Test
    fun futureCacheRefetches() =
        runTest {
            val cache = cacheStore()
            val key = "emotes:v1:FFZ_GLOBAL:twitch:global"
            cache.put(key, "[]", start + 1.days)
            val e = engine()
            loader(cache, e).load(twitch, setOf(EmoteProvider.FFZ), backgroundScope)
            assertEquals(2, e.requestHistory.size)
            assertEquals(start, cache.get(key)?.fetchedAt)
        }

    @Test
    fun failedSourceRetriesInBackground() =
        runTest {
            var bttvCalls = 0
            val e =
                engine {
                    if (it.has("/cached/users/twitch/") &&
                        bttvCalls++ == 0
                    ) {
                        HttpStatusCode.BadGateway
                    } else {
                        null
                    }
                }
            loader(cacheStore(), e).load(twitch, all, backgroundScope)
            assertEquals(0, count(EmoteProvider.BTTV, EmoteScope.CHANNEL))
            advanceTimeBy(5.seconds)
            runCurrent()
            assertEquals(bttvUserCount, count(EmoteProvider.BTTV, EmoteScope.CHANNEL))
            assertEquals(2, bttvCalls)
        }

    @Test
    fun retriesStopAfterFiveAttempts() =
        runTest {
            var calls = 0
            val e =
                engine {
                    if (it.has("/set/global")) {
                        calls++
                        HttpStatusCode.InternalServerError
                    } else {
                        null
                    }
                }
            loader(cacheStore(), e).load(twitch, setOf(EmoteProvider.FFZ), backgroundScope)
            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(1 + 5, calls)
        }

    @Test
    fun staleCacheUsedOnFailure() =
        runTest {
            val cache = cacheStore()
            val cached = listOf(emote("OldOne", EmoteProvider.BTTV, EmoteScope.CHANNEL))
            val key = "emotes:v1:BTTV_CHANNEL:twitch:1000500"
            cache.put(key, Json.encodeToString(listSerializer, cached), start - 2.hours)
            val e = engine { if (it.has("/cached/users/twitch/")) HttpStatusCode.InternalServerError else null }
            val results = loader(cache, e).load(twitch, all, backgroundScope)
            assertEquals(cached, registry.snapshot.value.sources[EmoteKey(EmoteProvider.BTTV, EmoteScope.CHANNEL)])
            assertIs<Outcome.Failure>(outcomeOf(results, EmoteSource.BTTV_CHANNEL))
        }

    private class FailingWrites(
        private val delegate: SqlDriver,
    ) : SqlDriver by delegate {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> = throw IllegalStateException("disk full")
    }

    @Test
    fun throwingNativeSourceIsIsolated() =
        runTest {
            val throwing = NativeEmoteSource { throw IllegalStateException("boom") }
            val results = loader(cacheStore(), engine(), throwing).load(twitch, all, backgroundScope)
            assertEquals(Outcome.Failure(LurkError.Unexpected), outcomeOf(results, EmoteSource.NATIVE))
            assertEquals(6, count(EmoteProvider.SEVENTV, EmoteScope.CHANNEL))
            assertIs<Outcome.Success<Int>>(outcomeOf(results, EmoteSource.FFZ_CHANNEL))
        }

    @Test
    fun throwingCacheWriteStillFillsRegistry() =
        runTest {
            val cache =
                CacheStore(
                    LurkDatabase(FailingWrites(inMemoryDriver())),
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                )
            val results = loader(cache, engine()).load(twitch, all, backgroundScope)
            assertEquals(6, results.size)
            assertEquals(6, count(EmoteProvider.SEVENTV, EmoteScope.CHANNEL))
        }

    @Test
    fun unexpectedRetryFailureNeitherEscapesNorCancelsScope() =
        runTest {
            var calls = 0
            val flaky =
                NativeEmoteSource {
                    if (calls++ == 0) throw IllegalStateException("first") else throw IllegalStateException("again")
                }
            loader(cacheStore(), engine(), flaky).load(twitch, setOf(EmoteProvider.FFZ), backgroundScope)
            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(6, calls)
            assertEquals(true, backgroundScope.isActive)
        }

    @Test
    fun htmlBodyKeepsStaleCacheAndRetries() =
        runTest {
            val cache = cacheStore()
            val cached = listOf(emote("OldOne", EmoteProvider.BTTV, EmoteScope.CHANNEL))
            val key = "emotes:v1:BTTV_CHANNEL:twitch:1000500"
            val cachedJson = Json.encodeToString(listSerializer, cached)
            cache.put(key, cachedJson, start - 2.hours)
            var bttvCalls = 0
            val e =
                engine(bodyOverride = {
                    if (it.has("/cached/users/twitch/") && bttvCalls++ == 0) fixture("broken/html.json") else null
                })
            val results = loader(cache, e).load(twitch, all, backgroundScope)

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcomeOf(results, EmoteSource.BTTV_CHANNEL))
            assertEquals(cached, registry.snapshot.value.sources[EmoteKey(EmoteProvider.BTTV, EmoteScope.CHANNEL)])
            assertEquals(cachedJson, cache.get(key)?.value, "the cache is not overwritten")
            assertEquals(start - 2.hours, cache.get(key)?.fetchedAt)

            advanceTimeBy(5.seconds)
            runCurrent()
            assertEquals(2, bttvCalls, "a retry was scheduled")
            assertEquals(bttvUserCount, count(EmoteProvider.BTTV, EmoteScope.CHANNEL))
        }

    @Test
    fun htmlBodyKeepsSevenTvSetIdAndRetries() =
        runTest {
            val cache = cacheStore()
            var userCalls = 0
            val e =
                engine(bodyOverride = {
                    val sevenTvUser = it.url.host == "7tv.io" && it.has("/users/")
                    if (sevenTvUser && userCalls++ == 1) fixture("broken/html.json") else null
                })
            val l = loader(cache, e)
            l.load(twitch, all, backgroundScope)
            val setId = "01AA0000000000000000000011"
            assertEquals(setId, l.sevenTvSetId.value)

            clock.now = start + 2.hours
            val results = l.load(twitch, all, backgroundScope)

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcomeOf(results, EmoteSource.SEVENTV_CHANNEL))
            assertEquals(setId, l.sevenTvSetId.value, "the set id is kept")
            assertEquals(6, count(EmoteProvider.SEVENTV, EmoteScope.CHANNEL))
            assertEquals(setId, cache.get("emotes:v1:SEVENTV_CHANNEL_SET:twitch:1000500")?.value)

            advanceTimeBy(5.seconds)
            runCurrent()
            assertEquals(3, userCalls, "a retry was scheduled")
            assertEquals(setId, l.sevenTvSetId.value)
        }
}

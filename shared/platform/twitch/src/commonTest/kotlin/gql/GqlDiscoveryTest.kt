package io.github.kemurixd.lurk.platform.twitch.gql

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.network.AlwaysOnline
import io.github.kemurixd.lurk.core.network.NetworkStatus
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GqlDiscoveryTest {
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

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private fun config(ids: List<String>) =
        MutableStateFlow(
            RemoteConfig(
                schemaVersion = SUPPORTED_SCHEMA,
                twitch =
                    TwitchConfig(
                        gqlClientId = "web-client-id",
                        persistedQueries = emptyMap(),
                        adCueClasses = emptyList(),
                        stitchedAdClasses = emptyList(),
                        recentMessagesUrl = "https://recent.example/",
                        gqlUrl = "https://gql.example/gql",
                        gqlDiscoveryClientIds = ids,
                    ),
                kick =
                    KickConfig(
                        apiBase = "https://kick.example/",
                        webApiBase = "https://kick.example/web/",
                        realtimeConnectionPath = "a",
                        realtimeAuthPath = "b",
                        publicChannelTemplates = emptyList(),
                    ),
                emotes = EmoteConfig(bttvZeroWidth = emptyList()),
            ),
        )

    private class Rig(
        val gql: GqlClient,
        val seen: List<HttpRequestData>,
        val times: List<Long>,
    )

    private fun TestScope.rig(
        ids: List<String> = listOf("android-id", "tv-id"),
        network: NetworkStatus = AlwaysOnline,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Rig {
        val seen = mutableListOf<HttpRequestData>()
        val times = mutableListOf<Long>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        seen += request
                        times += testScheduler.currentTime
                        handler(request)
                    }
                },
            )
        val gql =
            GqlClient(
                createHttpClient(engine, network = network),
                config(ids),
                DeviceId(MemoryStore()),
                TestDispatchers(dispatcher),
                null,
                testScheduler.timeSource,
                network,
            )
        return Rig(gql, seen, times)
    }

    private fun MockRequestHandleScope.reply(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val ok = """[{"data":{"v":1}}]"""
    private val integrity: String get() = fixture("gql/error-integrity.json")

    private suspend fun GqlClient.discover(after: String? = null): Outcome<Int> {
        val variables =
            buildJsonObject {
                put("first", 30)
                if (after != null) put("after", after)
            }
        return discoveryQuery("TopStreams", "query TopStreams { v }", variables, ::decodeV)
    }

    private fun decodeV(data: JsonObject): Int = data["v"]!!.jsonPrimitive.int

    @Test
    fun sendsTheAndroidIdWithoutAnAuthHeader() =
        runTest {
            val r = rig { reply(ok) }
            assertEquals(Outcome.Success(1), r.gql.discover())

            assertEquals("android-id", r.seen.single().headers["Client-Id"])
            assertNull(r.seen.single().headers[HttpHeaders.Authorization])
            assertTrue(
                Regex("[0-9a-f]{32}").matches(
                    r.seen
                        .single()
                        .headers["X-Device-Id"]
                        .orEmpty(),
                ),
            )
        }

    private class Switch(
        online: Boolean,
    ) : NetworkStatus {
        override val isOnline = MutableStateFlow(online)
    }

    @Test
    fun noNetworkFailsAsOfflineWithoutSendingAnything() =
        runTest {
            val r = rig(network = Switch(online = false)) { reply(ok) }
            assertEquals(Outcome.Failure(LurkError.Offline), r.gql.discover())
            assertEquals(0, r.seen.size)
        }

    @Test
    fun aServerErrorIsNotRetriedOnceTheNetworkIsGone() =
        runTest {
            val network = Switch(online = true)
            val r =
                rig(network = network) {
                    network.isOnline.value = false
                    reply("", HttpStatusCode.ServiceUnavailable)
                }
            assertEquals(Outcome.Failure(LurkError.Offline), r.gql.discover())
            assertEquals(1, r.seen.size)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun anIntegrityFailureFallsBackToTheTvIdAndSticksWithIt() =
        runTest {
            val r = rig { request -> if (request.headers["Client-Id"] == "android-id") reply(integrity) else reply(ok) }
            assertEquals(Outcome.Success(1), r.gql.discover())
            assertEquals(Outcome.Success(1), r.gql.discover())

            assertEquals(listOf("android-id", "tv-id", "tv-id"), r.seen.map { it.headers["Client-Id"] })
        }

    @Test
    fun everyIdFailingIsIntegrityRequiredAfterOneTryEach() =
        runTest {
            val r = rig { reply(integrity) }

            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), r.gql.discover())
            assertEquals(listOf("android-id", "tv-id"), r.seen.map { it.headers["Client-Id"] })
        }

    @Test
    fun anEmptyListFallsBackToTheWebId() =
        runTest {
            val r = rig(ids = emptyList()) { reply(ok) }
            assertEquals(Outcome.Success(1), r.gql.discover())
            assertEquals("web-client-id", r.seen.single().headers["Client-Id"])
        }

    @Test
    fun loadMoreWaitsHalfASecondAfterTheLastPageOfTheSameFeed() =
        runTest {
            val r = rig { reply(ok) }
            r.gql.discover()
            r.gql.discover(after = "c1")
            r.gql.discover(after = "c2")

            assertEquals(listOf(0L, 500L, 1000L), r.times)
        }

    @Test
    fun theFirstPageAndASlowLoadMoreAreNotDelayed() =
        runTest {
            val r = rig { reply(ok) }
            r.gql.discover(after = "c1")
            testScheduler.advanceTimeBy(2_000)
            r.gql.discover(after = "c2")
            r.gql.discover()

            assertEquals(listOf(0L, 2_000L, 2_000L), r.times)
        }

    @Test
    fun aRateLimitIsRetriedAfterOneTwoAndFourSeconds() =
        runTest {
            var calls = 0
            val r = rig { if (calls++ < 3) reply("{}", HttpStatusCode.TooManyRequests) else reply(ok) }

            assertEquals(Outcome.Success(1), r.gql.discover())
            assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), r.times)
        }

    @Test
    fun aServerErrorGivesUpAfterThreeRetries() =
        runTest {
            val r = rig { reply("{}", HttpStatusCode.BadGateway) }

            assertEquals(Outcome.Failure(LurkError.Http(502)), r.gql.discover())
            assertEquals(4, r.seen.size)
        }

    @Test
    fun aClientErrorIsNotRetried() =
        runTest {
            val r = rig { reply("{}", HttpStatusCode.Forbidden) }
            r.gql.discover()

            assertEquals(1, r.seen.size)
        }
}

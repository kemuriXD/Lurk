package io.github.kemurixd.lurk.platform.twitch.gql

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.network.ChallengeKind
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GqlClientTest {
    private class FakeSecureStore : SecureStore {
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

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private val config =
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

    private fun MockRequestHandleScope.reply(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun TestScope.client(
        solver: ChallengeSolver? = null,
        store: SecureStore = FakeSecureStore(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = GqlClient(
        createHttpClient(MockEngine(handler)),
        config,
        DeviceId(store),
        TestDispatchers(StandardTestDispatcher(testScheduler)),
        solver,
    )

    private suspend fun GqlClient.login(token: String? = null): Outcome<String> =
        query("LurkProbe", "query LurkProbe { user(login: \"a\") { id login } }", JsonObject(emptyMap()), token) {
            it["user"]!!.jsonObject["login"]!!.jsonPrimitive.content
        }

    @Test
    fun decodesTheDataObject() =
        runTest {
            val gql = client { reply(fixture("gql/user.json")) }
            assertEquals(Outcome.Success("streamer1"), gql.login())
        }

    @Test
    fun sendsClientIdDeviceIdAndOAuth() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val gql =
                client {
                    seen += it
                    reply(fixture("gql/user.json"))
                }
            gql.login(token = "tok-1")
            gql.login(token = null)

            val first = seen[0]
            assertEquals("https://gql.example/gql", first.url.toString())
            assertEquals("web-client-id", first.headers["Client-Id"])
            assertTrue(Regex("[0-9a-f]{32}").matches(first.headers["X-Device-Id"].orEmpty()))
            assertEquals("OAuth tok-1", first.headers[HttpHeaders.Authorization])
            assertNull(seen[1].headers[HttpHeaders.Authorization])
            assertEquals(first.headers["X-Device-Id"], seen[1].headers["X-Device-Id"])
        }

    @Test
    fun postsOneOperationInAnArray() =
        runTest {
            var sent = ""
            val gql =
                client {
                    sent = (it.body as TextContent).text
                    reply(fixture("gql/user.json"))
                }
            gql.login()
            val op =
                Json
                    .parseToJsonElement(sent)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("LurkProbe", op["operationName"]!!.jsonPrimitive.content)
            assertTrue(op["query"]!!.jsonPrimitive.content.startsWith("query LurkProbe"))
            assertTrue(op["variables"]!!.jsonObject.isEmpty())
        }

    @Test
    fun unauthorizedIsAuthExpired() =
        runTest {
            val http401 = client { reply("""{"error":"Unauthorized","status":401}""", HttpStatusCode.Unauthorized) }
            assertEquals(Outcome.Failure(LurkError.AuthExpired), http401.login("t"))

            val gqlError = client { reply("""[{"errors":[{"message":"invalid OAuth token"}]}]""") }
            assertEquals(Outcome.Failure(LurkError.AuthExpired), gqlError.login("t"))

            val unauthorized = client { reply("""[{"errors":[{"message":"Unauthorized"}]}]""") }
            assertEquals(Outcome.Failure(LurkError.AuthExpired), unauthorized.login("t"))
        }

    @Test
    fun otherHttpErrorsGoThroughMapHttpError() =
        runTest {
            val gql = client { reply("nope", HttpStatusCode.InternalServerError) }
            assertEquals(Outcome.Failure(LurkError.Http(500)), gql.login())
        }

    @Test
    fun integrityWithoutSolverIsIntegrityRequired() =
        runTest {
            var calls = 0
            val gql =
                client {
                    calls++
                    reply(fixture("gql/error-integrity.json"))
                }
            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), gql.login("tok"))
            assertEquals(1, calls)
        }

    @Test
    fun integrityWhileSignedOutIsIntegrityRequiredWithoutAskingTheSolver() =
        runTest {
            var solved = false
            val solver =
                ChallengeSolver { _, _ ->
                    solved = true
                    ChallengeResult.Solved(emptyMap(), null)
                }
            val gql = client(solver) { reply(fixture("gql/error-integrity.json")) }
            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), gql.login(null))
            assertFalse(solved)
        }

    @Test
    fun integritySolvedRetriesOnceWithHeader() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            var asked: ChallengeKind? = null
            val solver =
                ChallengeSolver { kind, _ ->
                    asked = kind
                    ChallengeResult.Solved(mapOf("client-integrity" to "x"), null)
                }
            val gql =
                client(solver) {
                    seen += it
                    if (seen.size == 1) reply(fixture("gql/error-integrity.json")) else reply(fixture("gql/user.json"))
                }
            assertEquals(Outcome.Success("streamer1"), gql.login("tok"))
            assertEquals(ChallengeKind.TWITCH_INTEGRITY, asked)
            assertEquals(2, seen.size)
            assertNull(seen[0].headers["Client-Integrity"])
            assertEquals("x", seen[1].headers["Client-Integrity"])
        }

    @Test
    fun secondIntegrityErrorIsIntegrityRequiredWithNoLoop() =
        runTest {
            var calls = 0
            val solver = ChallengeSolver { _, _ -> ChallengeResult.Solved(mapOf("client-integrity" to "x"), null) }
            val gql =
                client(solver) {
                    calls++
                    reply(fixture("gql/error-integrity.json"))
                }
            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), gql.login("tok"))
            assertEquals(2, calls)
        }

    @Test
    fun unsolvedChallengeIsIntegrityRequired() =
        runTest {
            val results =
                listOf(ChallengeResult.NeedsUser, ChallengeResult.Failed, ChallengeResult.Solved(emptyMap(), null))
            for (result in results) {
                var calls = 0
                val gql =
                    client({ _, _ -> result }) {
                        calls++
                        reply(fixture("gql/error-integrity.json"))
                    }
                assertEquals(Outcome.Failure(LurkError.IntegrityRequired), gql.login("tok"))
                assertEquals(1, calls)
            }
        }

    @Test
    fun deviceIdIsStable() =
        runTest {
            val store = FakeSecureStore()
            val first = DeviceId(store).get()
            val second = DeviceId(store).get()
            assertTrue(Regex("[0-9a-f]{32}").matches(first), first)
            assertEquals(first, second)
            assertEquals(first, store.values["twitch.deviceId"])
        }

    @Test
    fun garbageIsSchemaChanged() =
        runTest {
            for (body in listOf("not json", "{}", "[]", "[1]", """[{"hello":"world"}]""")) {
                assertEquals(Outcome.Failure(LurkError.SchemaChanged), client { reply(body) }.login(), body)
            }
        }

    @Test
    fun errorWithoutDataIsSchemaChanged() =
        runTest {
            val gql = client { reply(fixture("gql/error-schema.json")) }
            assertEquals(Outcome.Failure(LurkError.SchemaChanged), gql.login())
        }

    @Test
    fun decodeThatCannotReadTheShapeIsSchemaChanged() =
        runTest {
            val gql = client { reply(fixture("gql/user.json")) }
            val variables = buildJsonObject { put("a", 1) }
            val result = gql.query("LurkProbe", "q", variables, null) { it["missing"]!!.jsonObject }
            assertIs<Outcome.Failure>(result)
            assertEquals(LurkError.SchemaChanged, result.error)
        }
}

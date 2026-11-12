package io.github.kemurixd.lurk.platform.twitch.playback

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.github.kemurixd.lurk.platform.twitch.gql.DeviceId
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import io.github.kemurixd.lurk.playback.AdPolicy
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.github.kemurixd.lurk.playback.TokenFlags
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchPlaybackSourceTest {
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

    private class FakeFetcher(
        var result: Outcome<Account>,
    ) : ProfileFetcher {
        override val platform = Platform.TWITCH
        var calls = 0

        override suspend fun profile(token: String): Outcome<Account> {
            calls++
            return result
        }
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private val account = Account(Platform.TWITCH, "1000002", "viewer1", "Viewer One", null)
    private val userToken = "tok-secret-1"
    private val channel = ChannelRef(Platform.TWITCH, "streamer1")
    private val anonFlags = TokenFlags(subscriber = false, turbo = false, showAds = true)

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
                        usherBase = "https://usher.example",
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
        val source: TwitchPlaybackSource,
        val fetcher: FakeFetcher,
        val seen: List<HttpRequestData>,
    )

    private fun MockRequestHandleScope.reply(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun TestScope.rig(
        signedIn: Boolean,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Rig {
        val sessionStore = FakeSecureStore()
        if (signedIn) {
            sessionStore.values["twitch.token"] = userToken
            sessionStore.values["twitch.account"] = Json.encodeToString(account)
        }
        val fetcher = FakeFetcher(Outcome.Success(account))
        val sessions = SessionRepository(sessionStore, listOf(fetcher), backgroundScope)
        sessions.restore()
        // Runs restore's profile check now. advanceUntilIdle leaves background work queued, and the check would
        // then land in the middle of a grant.
        testScheduler.runCurrent()
        fetcher.calls = 0
        fetcher.result = Outcome.Failure(LurkError.AuthExpired)

        val seen = mutableListOf<HttpRequestData>()
        // The engine runs on the test scheduler too, so a request in flight holds virtual time still and only a
        // request that never answers lets the grant timeout fire.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        seen += request
                        handler(request)
                    }
                },
            )
        val gql =
            GqlClient(createHttpClient(engine), config, DeviceId(FakeSecureStore()), TestDispatchers(dispatcher), null)
        return Rig(TwitchPlaybackSource(gql, sessions, config, Random(1)), fetcher, seen)
    }

    private fun tokenValue(fixtureName: String): String =
        Json
            .parseToJsonElement(fixture("gql/$fixtureName"))
            .jsonArray[0]
            .jsonObject["data"]!!
            .jsonObject["streamPlaybackAccessToken"]!!
            .jsonObject["value"]!!
            .jsonPrimitive.content

    private fun Outcome<PlaybackGrant>.granted(): PlaybackGrant {
        assertIs<Outcome.Success<PlaybackGrant>>(this)
        return value
    }

    @Test
    fun describesItself() =
        runTest {
            val source = rig(signedIn = false) { reply(fixture("gql/playback-token-anon.json")) }.source
            assertEquals(Platform.TWITCH, source.platform)
            assertEquals(AdPolicy.TWITCH_DETECT_AND_PROXY, source.adPolicy)
        }

    @Test
    fun anonymousFlagsAreNotAdFree() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/playback-token-anon.json")) }
            val grant = r.source.grant(channel).granted()

            assertEquals(anonFlags, grant.tokenFlags)
            assertFalse(grant.tokenFlags!!.adFree)
            assertTrue(grant.lowLatencyCapable)
            assertTrue(grant.headers.isEmpty())
            assertNull(r.seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun grantCarriesTheFetchedTokenAndSignature() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/playback-token-anon.json")) }
            val url =
                Url(
                    r.source
                        .grant(channel)
                        .granted()
                        .masterUrl,
                )

            assertEquals("usher.example", url.host)
            assertEquals("/api/channel/hls/streamer1.m3u8", url.encodedPath)
            assertEquals(tokenValue("playback-token-anon.json"), url.parameters["token"])
            assertEquals("0".repeat(40), url.parameters["sig"])
        }

    @Test
    fun requestsTheSiteWebTokenForTheLogin() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/playback-token-anon.json")) }
            r.source.grant(channel)

            val op =
                Json
                    .parseToJsonElement((r.seen.single().body as TextContent).text)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("PlaybackAccessToken_Template", op["operationName"]!!.jsonPrimitive.content)
            val query = op["query"]!!.jsonPrimitive.content
            assertTrue("streamPlaybackAccessToken(channelName: \$login" in query, query)
            assertTrue(
                "params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}" in query,
                query,
            )
            assertFalse("videoPlaybackAccessToken" in query, query)
            assertEquals("streamer1", op["variables"]!!.jsonObject["login"]!!.jsonPrimitive.content)
        }

    @Test
    fun subscriberTokenIsAdFree() =
        runTest {
            val r = rig(signedIn = true) { reply(fixture("gql/playback-token-sub.json")) }
            val grant = r.source.grant(channel).granted()

            assertEquals(TokenFlags(subscriber = true, turbo = false, showAds = true), grant.tokenFlags)
            assertTrue(grant.tokenFlags!!.adFree)
            assertEquals("OAuth $userToken", r.seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun turboTokenIsAdFree() =
        runTest {
            val r = rig(signedIn = true) { reply(fixture("gql/playback-token-turbo.json")) }
            val flags =
                r.source
                    .grant(channel)
                    .granted()
                    .tokenFlags!!

            assertEquals(TokenFlags(subscriber = false, turbo = true, showAds = false), flags)
            assertTrue(flags.adFree)
        }

    @Test
    fun missingOrUnreadableFlagsNeverClaimAdFree() {
        assertEquals(anonFlags, tokenFlagsOf("{}"))
        assertEquals(anonFlags, tokenFlagsOf("not json"))
        assertEquals(anonFlags, tokenFlagsOf("[]"))
        assertEquals(anonFlags, tokenFlagsOf("""{"subscriber":"yes","turbo":null,"show_ads":"no"}"""))
        assertFalse(tokenFlagsOf("{}").adFree)
    }

    @Test
    fun expiredTokenFallsBackToAnonymousGrant() =
        runTest {
            val r =
                rig(signedIn = true) { request ->
                    if (request.headers[HttpHeaders.Authorization] != null) {
                        reply("""{"error":"Unauthorized","status":401}""", HttpStatusCode.Unauthorized)
                    } else {
                        reply(fixture("gql/playback-token-anon.json"))
                    }
                }
            val grant = r.source.grant(channel).granted()

            assertEquals(anonFlags, grant.tokenFlags)
            assertEquals(1, r.fetcher.calls)
            assertEquals(2, r.seen.size)
            assertEquals("OAuth $userToken", r.seen[0].headers[HttpHeaders.Authorization])
            assertNull(r.seen[1].headers[HttpHeaders.Authorization])
        }

    @Test
    fun integrityWithoutSolverFallsBackAnonymous() =
        runTest {
            val r =
                rig(signedIn = true) { request ->
                    if (request.headers[HttpHeaders.Authorization] != null) {
                        reply(fixture("gql/error-integrity.json"))
                    } else {
                        reply(fixture("gql/playback-token-anon.json"))
                    }
                }
            val grant = r.source.grant(channel).granted()

            assertEquals(anonFlags, grant.tokenFlags)
            assertEquals(2, r.seen.size)
            assertEquals("OAuth $userToken", r.seen[0].headers[HttpHeaders.Authorization])
            assertNull(r.seen[1].headers[HttpHeaders.Authorization])
            assertEquals(0, r.fetcher.calls)
        }

    @Test
    fun anonymousIntegrityFailureIsReturnedWithoutALoop() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/error-integrity.json")) }

            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), r.source.grant(channel))
            assertEquals(1, r.seen.size)
        }

    @Test
    fun hangingGrantTimesOut() =
        runTest {
            val r = rig(signedIn = true) { awaitCancellation() }
            val start = currentTime

            assertEquals(Outcome.Failure(LurkError.Timeout), r.source.grant(channel))
            assertEquals(10_000L, currentTime - start)
        }

    @Test
    fun unknownChannelIsNotFound() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/playback-token-unknown.json")) }
            assertEquals(Outcome.Failure(LurkError.NotFound), r.source.grant(channel))
        }

    @Test
    fun tokenWithoutValueIsSchemaChanged() =
        runTest {
            val body = """[{"data":{"streamPlaybackAccessToken":{"value":null,"signature":"0"}}}]"""
            val r = rig(signedIn = false) { reply(body) }
            assertEquals(Outcome.Failure(LurkError.SchemaChanged), r.source.grant(channel))
        }

    @Test
    fun grantToStringHasNoToken() =
        runTest {
            val r = rig(signedIn = false) { reply(fixture("gql/playback-token-anon.json")) }
            val text =
                r.source
                    .grant(channel)
                    .granted()
                    .toString()

            assertFalse("sig=" in text, text)
            assertFalse("token=" in text, text)
            assertFalse(tokenValue("playback-token-anon.json") in text, text)
            assertFalse("192.0.2.1" in text, text)
        }
}

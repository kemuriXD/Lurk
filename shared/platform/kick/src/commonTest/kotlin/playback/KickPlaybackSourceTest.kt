package io.github.kemurixd.lurk.platform.kick.playback

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.playback.AdPolicy
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KickPlaybackSourceTest {
    private class EmptyStore : SecureStore {
        override suspend fun get(key: String): String? = null

        override suspend fun put(
            key: String,
            value: String,
        ) = Unit

        override suspend fun remove(key: String) = Unit

        override suspend fun clear() = Unit
    }

    private class NoFetcher : ProfileFetcher {
        override val platform = Platform.KICK

        override suspend fun profile(token: String): Outcome<Account> = Outcome.Failure(LurkError.AuthExpired)
    }

    private val channel = ChannelRef(Platform.KICK, "streamer1")
    private val liveUrl = "https://playback.example/api/video/v1/x.m3u8?token=0"

    private fun TestScope.source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KickPlaybackSource(
            Rig(mockEngine(handler)).api,
            SessionRepository(EmptyStore(), listOf(NoFetcher()), backgroundScope),
        )

    private fun Outcome<PlaybackGrant>.granted(): PlaybackGrant {
        assertIs<Outcome.Success<PlaybackGrant>>(this)
        return value
    }

    @Test
    fun describesItself() =
        runTest {
            val s = source { json(fixture("api/channel-live.json")) }
            assertEquals(Platform.KICK, s.platform)
            assertEquals(AdPolicy.UNTOUCHED, s.adPolicy)
        }

    @Test
    fun liveChannelGrant() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val s =
                source {
                    seen += it
                    json(fixture("api/channel-live.json"))
                }
            val grant = s.grant(channel).granted()

            assertEquals(liveUrl, grant.masterUrl)
            assertNull(grant.tokenFlags)
            assertTrue(grant.lowLatencyCapable)
            assertEquals("https://kick.example/api/v2/channels/streamer1", seen.single().url.toString())
        }

    @Test
    fun offlineChannelIsNotFound() =
        runTest {
            val s = source { json(fixture("api/channel-offline.json")) }
            assertEquals(Outcome.Failure(LurkError.NotFound), s.grant(channel))
        }

    @Test
    fun finishedLivestreamIsNotFound() =
        runTest {
            val body = fixture("api/channel-live.json").replace("\"is_live\": true", "\"is_live\": false")
            val s = source { json(body) }
            assertEquals(Outcome.Failure(LurkError.NotFound), s.grant(channel))
        }

    @Test
    fun blankPlaybackUrlIsNotFound() =
        runTest {
            val body = fixture("api/channel-live.json").replace(liveUrl, " ")
            val s = source { json(body) }
            assertEquals(Outcome.Failure(LurkError.NotFound), s.grant(channel))
        }

    @Test
    fun hangingGrantTimesOut() =
        runTest {
            val s = source { awaitCancellation() }
            val start = currentTime

            assertEquals(Outcome.Failure(LurkError.Timeout), s.grant(channel))
            assertEquals(10_000L, currentTime - start)
        }

    @Test
    fun notFoundSlug() =
        runTest {
            val s = source { json("""{"message":"not found"}""", HttpStatusCode.NotFound) }
            assertEquals(Outcome.Failure(LurkError.NotFound), s.grant(channel))
        }

    @Test
    fun noAdDataRead() =
        runTest {
            val plain = Json.parseToJsonElement(fixture("api/channel-live.json")).jsonObject
            val ads =
                buildJsonObject {
                    put("enabled", true)
                    put("break_url", "https://ads.example/x")
                }
            val withAds = JsonObject(plain + ("ads" to ads))
            assertFalse("ads" in plain)

            val a = source { json(plain.toString()) }.grant(channel)
            val b = source { json(withAds.toString()) }.grant(channel)

            assertEquals(a, b)
            assertEquals(liveUrl, a.granted().masterUrl)
        }

    @Test
    fun slugIsOnePathSegment() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val source =
                source {
                    seen += it
                    json(fixture("api/channel-live.json"))
                }
            source.grant(ChannelRef(Platform.KICK, "a/b?c#d"))

            assertEquals("/api/v2/channels/a%2Fb%3Fc%23d", seen.single().url.encodedPath)
        }
}

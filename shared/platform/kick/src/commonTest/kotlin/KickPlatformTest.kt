package io.github.kemurixd.lurk.platform.kick

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.platform.api.Capabilities
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.api.rigConfig
import io.github.kemurixd.lurk.platform.kick.playback.KickPlaybackSource
import io.github.kemurixd.lurk.platform.kick.realtime.RealtimeBootstrap
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KickPlatformTest {
    private fun channel(
        id: String,
        chatId: String,
    ) = Channel(
        ref = ChannelRef(Platform.KICK, "streamer1"),
        id = id,
        chatId = chatId,
        displayName = "Streamer One",
        avatarUrl = null,
        bannerUrl = null,
        bio = null,
        followers = null,
        live = null,
        lastStream = null,
    )

    private suspend fun TestScope.platform(
        seen: MutableList<HttpRequestData> = mutableListOf(),
        signedIn: Boolean = false,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): KickPlatform {
        val rig =
            Rig(
                mockEngine {
                    seen += it
                    json(if (status == HttpStatusCode.OK) fixture("api/channel-live.json") else "{}", status)
                },
            )
        val sessions = rigSessions(signedIn)
        return KickPlatform(
            api = rig.api,
            sessions = sessions,
            bootstrap = RealtimeBootstrap(rig.api, rigConfig),
            connector = WebSocketConnector { _, _ -> error("not opened in these tests") },
            playback = KickPlaybackSource(rig.api, sessions),
        )
    }

    @Test
    fun kickCapabilities() =
        runTest {
            val expected =
                Capabilities(
                    follows = true,
                    followMutation = true,
                    chatSend = true,
                    chatHistory = true,
                    cheermotes = false,
                )
            val platform = platform()
            assertEquals(expected, platform.capabilities)
            assertEquals(Platform.KICK, platform.id)
        }

    @Test
    fun kickCheermotesEmpty() =
        runTest {
            assertEquals(emptyList(), platform().cheermotes.load("1000001"))
        }

    @Test
    fun setFollowSignedOutIsNotSignedIn() =
        runTest {
            assertEquals(
                Outcome.Failure(LurkError.NotSignedIn),
                platform().setFollow(channel("1000001", "1000002"), true),
            )
        }

    @Test
    fun chatWithKnownIdsDoesNotResolve() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val endpoints = platform(seen).chat(channel("1000001", "1000002"))
            assertEquals(0, seen.size)
            assertEquals("streamer1", endpoints.channel.login)
            assertNull(endpoints.sender)
            assertNotNull(endpoints.history)
        }

    @Test
    fun blankIdsAreResolvedBeforeChat() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val endpoints = platform(seen, signedIn = true).chat(channel("", ""))
            assertEquals("/api/v2/channels/streamer1", seen.first().url.encodedPath)
            assertNotNull(endpoints.sender)
            endpoints.history!!.fetch()
            assertEquals("/api/v1/chat/1000001/history", seen.last().url.encodedPath)
        }

    @Test
    fun failedResolveThrowsItsError() =
        runTest {
            val thrown =
                assertFailsWith<LurkException> {
                    platform(status = HttpStatusCode.NotFound).chat(channel("", ""))
                }
            assertEquals(LurkError.NotFound, thrown.error)
        }
}

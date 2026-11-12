package io.github.kemurixd.lurk.platform.twitch

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.platform.api.Capabilities
import io.github.kemurixd.lurk.platform.twitch.chat.FakeIrcConnector
import io.github.kemurixd.lurk.platform.twitch.chat.chatConfig
import io.github.kemurixd.lurk.platform.twitch.discovery.RIG_TOKEN
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.playback.TwitchPlaybackSource
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchPlatformTest {
    private val channel =
        Channel(
            ref = ChannelRef(Platform.TWITCH, "streamer1"),
            id = "1000001",
            chatId = "streamer1",
            displayName = "Streamer One",
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = null,
            lastStream = null,
        )

    private suspend fun TestScope.platform(
        signedIn: Boolean,
        connector: WebSocketConnector = WebSocketConnector { _, _ -> error("not opened in these tests") },
    ): TwitchPlatform {
        val rig = discoveryRig(signedIn) { reply("[]") }
        return TwitchPlatform(
            gql = rig.gql,
            sessions = rig.sessions,
            http = createHttpClient(MockEngine { reply("") }),
            connector = connector,
            config = chatConfig,
            scope = backgroundScope,
            playback = TwitchPlaybackSource(rig.gql, rig.sessions, chatConfig),
        )
    }

    @Test
    fun twitchCapabilities() =
        runTest {
            val expected =
                Capabilities(
                    follows = true,
                    followMutation = false,
                    chatSend = true,
                    chatHistory = true,
                    cheermotes = true,
                )
            val platform = platform(signedIn = false)
            assertEquals(expected, platform.capabilities)
            assertEquals(Platform.TWITCH, platform.id)
        }

    @Test
    fun twitchSetFollowUnsupported() =
        runTest {
            assertEquals(
                Outcome.Failure(LurkError.Unsupported),
                platform(signedIn = true).setFollow(channel, true),
            )
        }

    @Test
    fun signedOutChatHasNoSender() =
        runTest {
            val endpoints = platform(signedIn = false).chat(channel)
            assertNull(endpoints.sender)
            assertNotNull(endpoints.history)
            assertEquals("streamer1", endpoints.channel.login)
            assertEquals("1000001", endpoints.channel.channelId)
            assertEquals(Platform.TWITCH, endpoints.channel.platform)
        }

    @Test
    fun signedInChatHasASender() =
        runTest {
            assertNotNull(platform(signedIn = true).chat(channel).sender)
        }

    @Test
    fun signedInChatLogsInAsTheAccount() =
        runTest {
            val connector = FakeIrcConnector()
            val endpoints = platform(signedIn = true, connector = connector).chat(channel)
            backgroundScope.launch { endpoints.transport.frames().collect { } }
            runCurrent()
            assertEquals(Outcome.Success(Unit), endpoints.sender?.send("hi", null))

            for (connection in connector.connections) {
                assertTrue("NICK viewer1" in connection.sent, connection.sent.toString())
                assertTrue("PASS oauth:$RIG_TOKEN" in connection.sent)
            }
            assertEquals(2, connector.connections.size)
        }

    @Test
    fun signedOutChatLogsInAnonymously() =
        runTest {
            val connector = FakeIrcConnector()
            val endpoints = platform(signedIn = false, connector = connector).chat(channel)
            backgroundScope.launch { endpoints.transport.frames().collect { } }
            runCurrent()

            val sent = connector.connections.single().sent
            assertTrue(sent.any { Regex("NICK justinfan[0-9]{4,5}").matches(it) }, sent.toString())
            assertTrue(sent.none { it.startsWith("PASS") })
        }
}

package io.github.kemurixd.lurk.platform.twitch.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IrcTransportTest {
    private fun transport(
        connector: FakeIrcConnector,
        token: String? = null,
    ) = IrcTransport(connector, chatConfig, "streamer1", { token }, Random(1))

    @Test
    fun anonymousHandshake() =
        runTest {
            val connector = FakeIrcConnector()
            val frames = mutableListOf<String>()
            backgroundScope.launch { transport(connector).frames().collect { frames += it } }
            runCurrent()
            val sent = connector.connections.single().sent
            assertEquals(listOf(TEST_IRC_URL), connector.urls)
            assertEquals(3, sent.size)
            assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands", sent[0])
            assertTrue(Regex("NICK justinfan[0-9]{4,5}").matches(sent[1]), sent[1])
            assertEquals("JOIN #streamer1", sent[2])
            assertFalse(sent.any { it.startsWith("PASS") })
        }

    @Test
    fun signedInHandshakeHasPass() =
        runTest {
            val connector = FakeIrcConnector()
            val t = transport(connector, "tok")
            backgroundScope.launch { t.frames().collect { } }
            runCurrent()
            val sent = connector.connections.single().sent
            assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands", sent[0])
            assertEquals("PASS oauth:tok", sent[1])
            assertTrue(sent[2].startsWith("NICK ") && sent[2].length > "NICK ".length)
            assertEquals("JOIN #streamer1", sent[3])
            assertFalse(t.toString().contains("tok"))
        }

    @Test
    fun pingGetsPong() =
        runTest {
            val connector = FakeIrcConnector()
            backgroundScope.launch { transport(connector).frames().collect { } }
            runCurrent()
            val connection = connector.connections.single()
            connection.inbound.send("PING :tmi.twitch.tv\r\n")
            runCurrent()
            assertEquals("PONG :tmi.twitch.tv", connection.sent.last())
        }

    @Test
    fun framesPassThrough() =
        runTest {
            val connector = FakeIrcConnector()
            val frames = mutableListOf<String>()
            backgroundScope.launch { transport(connector).frames().collect { frames += it } }
            runCurrent()
            val connection = connector.connections.single()
            connection.inbound.send("PING :x\r\n")
            connection.inbound.send(":a PRIVMSG #streamer1 :hi\r\n:b PRIVMSG #streamer1 :yo\r\n")
            runCurrent()
            assertEquals(listOf("PING :x\r\n", ":a PRIVMSG #streamer1 :hi\r\n:b PRIVMSG #streamer1 :yo\r\n"), frames)
        }

    @Test
    fun socketCloseCompletesFlow() =
        runTest {
            val connector = FakeIrcConnector()
            var completed = false
            backgroundScope.launch {
                transport(connector).frames().collect { }
                completed = true
            }
            runCurrent()
            val connection = connector.connections.single()
            connection.inbound.close()
            runCurrent()
            assertTrue(completed)
            assertTrue(connection.closed)
        }

    @Test
    fun cancelClosesConnection() =
        runTest {
            val connector = FakeIrcConnector()
            val job = backgroundScope.launch { transport(connector).frames().collect { } }
            runCurrent()
            val connection = connector.connections.single()
            assertFalse(connection.closed)
            job.cancel()
            runCurrent()
            assertTrue(connection.closed)
        }
}

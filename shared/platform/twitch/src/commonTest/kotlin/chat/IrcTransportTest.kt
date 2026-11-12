package io.github.kemurixd.lurk.platform.twitch.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
        credential: IrcCredential? = null,
        rejected: MutableList<String> = mutableListOf(),
    ) = IrcTransport(connector, chatConfig, "streamer1", { credential }, { rejected += it }, Random(1))

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
    fun signedInHandshakeUsesTheUserLogin() =
        runTest {
            val connector = FakeIrcConnector()
            val t = transport(connector, IrcCredential("tok", "Viewer1"))
            backgroundScope.launch { t.frames().collect { } }
            runCurrent()
            val sent = connector.connections.single().sent
            assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands", sent[0])
            assertEquals("PASS oauth:tok", sent[1])
            assertEquals("NICK viewer1", sent[2])
            assertEquals("JOIN #streamer1", sent[3])
            assertFalse(t.toString().contains("tok"))
            assertFalse(t.toString().contains("viewer1", ignoreCase = true))
        }

    @Test
    fun credentialNeverPrintsTokenOrLogin() {
        val text = IrcCredential("tok-secret-1", "Viewer1").toString()
        assertFalse(text.contains("tok-secret-1"), text)
        assertFalse(text.contains("viewer1", ignoreCase = true), text)
    }

    @Test
    fun loginFailureReportsTheTokenOnceAndCompletes() =
        runTest {
            for (notice in listOf("Login authentication failed", "Improperly formatted auth")) {
                val connector = FakeIrcConnector()
                val rejected = mutableListOf<String>()
                val frames = mutableListOf<String>()
                var completed = false
                backgroundScope.launch {
                    transport(connector, IrcCredential("tok", "viewer1"), rejected).frames().collect { frames += it }
                    completed = true
                }
                runCurrent()
                val connection = connector.connections.single()
                connection.inbound.send(":tmi.twitch.tv NOTICE * :$notice\r\n")
                runCurrent()
                assertEquals(listOf("tok"), rejected, notice)
                assertTrue(completed, notice)
                assertTrue(connection.closed, notice)
                assertTrue(frames.isEmpty(), notice)
            }
        }

    @Test
    fun loginFailureWhileSignedOutIsNotReported() =
        runTest {
            val connector = FakeIrcConnector()
            val rejected = mutableListOf<String>()
            backgroundScope.launch { transport(connector, rejected = rejected).frames().collect { } }
            runCurrent()
            connector.connections
                .single()
                .inbound
                .send(":tmi.twitch.tv NOTICE * :Login authentication failed\r\n")
            runCurrent()
            assertTrue(rejected.isEmpty())
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
    fun pongIsSentWhileTheCollectorIsBusy() =
        runTest {
            val connector = FakeIrcConnector()
            backgroundScope.launch { transport(connector).frames().collect { awaitCancellation() } }
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

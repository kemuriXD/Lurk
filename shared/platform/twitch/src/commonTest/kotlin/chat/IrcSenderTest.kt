package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IrcSenderTest {
    private fun sender(
        connector: FakeIrcConnector,
        token: String? = "tok",
        tokenCalls: MutableList<Unit> = mutableListOf(),
    ) = IrcSender(connector, chatConfig, "streamer1", {
        tokenCalls += Unit
        token
    })

    @Test
    fun sendFormatsPrivmsg() =
        runTest {
            val connector = FakeIrcConnector()
            val s = sender(connector)
            assertEquals(Outcome.Success(Unit), s.send("hello there", null))
            assertEquals(
                "PRIVMSG #streamer1 :hello there",
                connector.connections
                    .single()
                    .sent
                    .last(),
            )
            assertTrue(
                connector.connections
                    .single()
                    .sent
                    .contains("PASS oauth:tok"),
            )
            assertFalse(s.toString().contains("tok"))
        }

    @Test
    fun replyHasParentTag() =
        runTest {
            val connector = FakeIrcConnector()
            sender(connector).send("hi", "abc-123")
            assertEquals(
                "@reply-parent-msg-id=abc-123 PRIVMSG #streamer1 :hi",
                connector.connections
                    .single()
                    .sent
                    .last(),
            )
        }

    @Test
    fun newlinesFlattened() =
        runTest {
            val connector = FakeIrcConnector()
            sender(connector).send("one\r\ntwo\nthree", null)
            assertEquals(
                "PRIVMSG #streamer1 :one  two three",
                connector.connections
                    .single()
                    .sent
                    .last(),
            )
        }

    @Test
    fun signedOutSendIsNotSignedIn() =
        runTest {
            val connector = FakeIrcConnector()
            assertEquals(Outcome.Failure(LurkError.NotSignedIn), sender(connector, token = null).send("hi", null))
            assertTrue(connector.connections.isEmpty())
        }

    @Test
    fun keepsOneConnection() =
        runTest {
            val connector = FakeIrcConnector()
            val calls = mutableListOf<Unit>()
            val s = sender(connector, tokenCalls = calls)
            s.send("a", null)
            s.send("b", null)
            assertEquals(1, connector.connections.size)
            assertEquals(1, calls.size)
        }

    @Test
    fun sendReconnectsOnceAfterDrop() =
        runTest {
            val connector = FakeIrcConnector()
            val s = sender(connector)
            s.send("a", null)
            connector.connections.first().failSends = true
            assertEquals(Outcome.Success(Unit), s.send("b", null))
            assertEquals(2, connector.connections.size)
            assertTrue(connector.connections[0].closed)
            assertEquals("PRIVMSG #streamer1 :b", connector.connections[1].sent.last())
            assertTrue(connector.connections[1].sent.contains("PASS oauth:tok"))
        }

    @Test
    fun secondFailureIsReturned() =
        runTest {
            val connector = FakeIrcConnector { FakeIrcConnection().also { it.failSends = true } }
            val result = sender(connector).send("a", null)
            assertEquals(Outcome.Failure(LurkError.Offline), result)
            assertEquals(2, connector.connections.size)
        }
}

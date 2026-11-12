package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.chat.ChatParsers
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.platform.kick.RIG_TOKEN
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.platform.kick.rigSessions
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val CONNECT_ERROR = """{"id":1,"error":{"code":109,"message":"token expired"}}"""
private const val SETTLE_MS = 10_000L
private const val DISCONNECT_PUSH = """{"push":{"disconnect":{"code":3005,"reason":"expired","reconnect":true}}}"""

class CentrifugoTransportTest {
    private val recordedFrames = fixture("realtime/centrifugo-frames.txt").lines().filter { it.isNotBlank() }

    private suspend fun TestScope.transport(
        bootstrap: RealtimeBootstrap,
        connector: FakeSocketConnector,
        sessions: SessionRepository? = null,
    ) = CentrifugoTransport(bootstrap, connector, CHANNEL_ID, CHATROOM_ID, sessions ?: rigSessions())

    @Test
    fun connectThenSubscribeAll() =
        runTest {
            val socket = FakeSocket().apply { inbound.close() }
            transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(
                listOf(
                    """{"id":1,"connect":{"token":"$FIXTURE_TOKEN","name":"js"}}""",
                    """{"id":2,"subscribe":{"channel":"chatroom_$CHATROOM_ID"}}""",
                    """{"id":3,"subscribe":{"channel":"channel_$CHANNEL_ID"}}""",
                    """{"id":4,"subscribe":{"channel":"chatrooms.$CHATROOM_ID"}}""",
                    """{"id":5,"subscribe":{"channel":"chatrooms.$CHATROOM_ID.v2"}}""",
                    """{"id":6,"subscribe":{"channel":"channel.$CHANNEL_ID"}}""",
                ),
                socket.sent,
            )
        }

    @Test
    fun connectsToTheTicketSocket() =
        runTest {
            val connector = FakeSocketConnector { FakeSocket().apply { inbound.close() } }
            transport(bootstrapRig(mutableListOf()), connector).frames().toList()

            assertEquals(listOf(SOCKET_URL), connector.urls)
        }

    @Test
    fun pingAnsweredWithEmptyObject() =
        runTest {
            val socket = FakeSocket()
            socket.inbound.send("{}")
            socket.inbound.close()
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(listOf("{}"), frames)
            assertEquals("{}", socket.sent.last())
            assertEquals(7, socket.sent.size, "six handshake commands, then one pong")
        }

    @Test
    fun pushFramesEmitted() =
        runTest {
            val socket = FakeSocket()
            recordedFrames.forEach { socket.inbound.send(it) }
            socket.inbound.close()
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(recordedFrames, frames)
            val messages = frames.flatMap(ChatParsers.kick::parse).filterIsInstance<ChatEvent.Message>()
            assertTrue(messages.isNotEmpty())
            assertEquals(CHATROOM_ID, messages.first().message.channelId)
            assertEquals("Chat message 1", messages.first().message.text)
        }

    @Test
    fun serverDisconnectCompletesFlow() =
        runTest {
            val socket = FakeSocket()
            socket.inbound.send(recordedFrames.first())
            socket.inbound.send(DISCONNECT_PUSH)
            socket.inbound.send(recordedFrames.last())
            // The channel stays open: only the disconnect frame can end this collection.
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(listOf(recordedFrames.first(), DISCONNECT_PUSH), frames)
            assertTrue(socket.closed)
        }

    @Test
    fun connectErrorReplyCompletesFlow() =
        runTest {
            val socket = FakeSocket()
            socket.inbound.send(CONNECT_ERROR)
            socket.inbound.send(recordedFrames.first())
            // The channel stays open: only the error reply can end this collection.
            val frames =
                withTimeout(SETTLE_MS) {
                    transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()
                }

            assertEquals(listOf(CONNECT_ERROR), frames)
            assertTrue(socket.closed)
        }

    @Test
    fun errorReplyToASubscribeKeepsFlowOpen() =
        runTest {
            val subscribeError = CONNECT_ERROR.replace("\"id\":1,", "\"id\":3,")
            val socket = FakeSocket()
            socket.inbound.send(subscribeError)
            socket.inbound.send(recordedFrames.first())
            socket.inbound.close()
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(listOf(subscribeError, recordedFrames.first()), frames)
        }

    @Test
    fun socketCloseCompletesFlow() =
        runTest {
            val socket = FakeSocket()
            socket.inbound.send(recordedFrames.first())
            socket.inbound.close()
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(listOf(recordedFrames.first()), frames)
            assertTrue(socket.closed)
        }

    @Test
    fun chatMentioningDisconnectKeepsFlowOpen() =
        runTest {
            val chatty = recordedFrames.last().replace("Chat message 3", "disconnect")
            val socket = FakeSocket()
            socket.inbound.send(chatty)
            socket.inbound.send(recordedFrames.first())
            socket.inbound.close()
            val frames = transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()

            assertEquals(listOf(chatty, recordedFrames.first()), frames)
        }

    @Test
    fun closesWhenTheCollectorStops() =
        runTest {
            val socket = FakeSocket()
            recordedFrames.forEach { socket.inbound.send(it) }
            val frames =
                transport(
                    bootstrapRig(mutableListOf()),
                    FakeSocketConnector { socket },
                ).frames().take(2).toList()

            assertEquals(2, frames.size)
            assertTrue(socket.closed)
        }

    @Test
    fun closesOnFailedHandshake() =
        runTest {
            val socket = FakeSocket().apply { failSends = true }
            assertFailsWith<LurkException> {
                transport(bootstrapRig(mutableListOf()), FakeSocketConnector { socket }).frames().toList()
            }

            assertTrue(socket.closed)
        }

    @Test
    fun reconnectRebootstraps() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            var auths = 0
            val bootstrap =
                bootstrapRig(seen) { request ->
                    recorded(request, auth = {
                        auths++
                        """{"data":{"token":"$FIXTURE_TOKEN$auths"},"message":"success"}"""
                    })
                }
            val connector = FakeSocketConnector { FakeSocket().apply { inbound.close() } }
            val transport = transport(bootstrap, connector)

            transport.frames().toList()
            transport.frames().toList()

            assertEquals(6, seen.size, "the whole bootstrap runs again on each collection")
            val clientIds = seen.filter { it.url.encodedPath == AUTH_PATH }.map { it.bodyText() }
            assertEquals(2, clientIds.size)
            assertNotEquals(clientIds[0], clientIds[1], "signed out, each run gets a fresh client id")
            val connects = connector.sockets.map { it.sent.first() }
            assertEquals(
                listOf(
                    """{"id":1,"connect":{"token":"${FIXTURE_TOKEN}1","name":"js"}}""",
                    """{"id":1,"connect":{"token":"${FIXTURE_TOKEN}2","name":"js"}}""",
                ),
                connects,
                "a fresh connection token each time, never a cached one",
            )
        }

    @Test
    fun bootstrapFailureThrowsLurkException() =
        runTest {
            val connector = FakeSocketConnector()
            val bootstrap =
                bootstrapRig(mutableListOf()) {
                    if (it.url.encodedPath == CHAT_CONNECTION_PATH) {
                        json("""{"message":"bad request"}""", HttpStatusCode.BadRequest)
                    } else {
                        recorded(it)
                    }
                }
            val error = assertFailsWith<LurkException> { transport(bootstrap, connector).frames().toList() }

            assertTrue(connector.urls.isEmpty(), "no socket without a ticket")
            assertFalse(FIXTURE_TOKEN in error.message.orEmpty())
        }

    @Test
    fun expiredTokenBootstrapsAgainSignedOut() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val bootstrap =
                bootstrapRig(seen) {
                    if (it.headers[HttpHeaders.Authorization] != null) {
                        json("""{"message":"Unauthenticated."}""", HttpStatusCode.Unauthorized)
                    } else {
                        recorded(it)
                    }
                }
            val socket = FakeSocket().apply { inbound.close() }
            val connector = FakeSocketConnector { socket }

            transport(bootstrap, connector, rigSessions(signedIn = true)).frames().toList()

            assertEquals("Bearer $RIG_TOKEN", seen.first().headers[HttpHeaders.Authorization])
            assertEquals(4, seen.size, "one signed-in POST is rejected, then the whole bootstrap runs signed out")
            assertTrue(seen.drop(1).all { it.headers[HttpHeaders.Authorization] == null })
            assertEquals(listOf(SOCKET_URL), connector.urls)
            assertEquals("""{"id":1,"connect":{"token":"$FIXTURE_TOKEN","name":"js"}}""", socket.sent.first())
        }

    @Test
    fun transportNeverPrintsToken() =
        runTest {
            val transport = transport(bootstrapRig(mutableListOf()), FakeSocketConnector())

            assertFalse(FIXTURE_TOKEN in transport.toString())
        }
}

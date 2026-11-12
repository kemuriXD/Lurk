package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TIMEOUT_MS = 10_000L
private const val TOTAL_TIMEOUT_MS = 60_000L
private const val RACE_ITERATIONS = 200

class KtorWebSocketConnectorTest {
    private var server: EmbeddedServer<*, *>? = null
    private val client: HttpClient = createHttpClient()

    @AfterTest
    fun tearDown() {
        client.close()
        server?.stop(0, 0)
    }

    private fun startServer(routes: io.ktor.server.routing.Routing.() -> Unit): String {
        val started =
            embeddedServer(CIO, port = 0) {
                install(WebSockets)
                routing(routes)
            }.start(wait = false)
        server = started
        val port =
            runBlocking {
                started.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        return "ws://127.0.0.1:$port/ws"
    }

    @Test
    fun echoesTextFrames() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        for (frame in incoming) {
                            if (frame is Frame.Text) send(Frame.Text(frame.readText()))
                        }
                    }
                }
            val connection = KtorWebSocketConnector(client).connect(url, emptyMap())
            connection.send("a")
            assertEquals("a", withTimeout(TIMEOUT_MS) { connection.incoming.first() })
            connection.close()
        }

    @Test
    fun serverCloseCompletesIncoming() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        send(Frame.Text("hello"))
                        close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
                    }
                }
            val connection = KtorWebSocketConnector(client).connect(url, emptyMap())
            val received = withTimeout(TIMEOUT_MS) { connection.incoming.toList() }
            assertEquals(listOf("hello"), received)
            connection.close()
            connection.close()
        }

    @Test
    fun connectToClosedPortFails() =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            val failure =
                assertFailsWith<LurkException> {
                    KtorWebSocketConnector(client).connect("ws://127.0.0.1:$port/ws", emptyMap())
                }
            assertTrue(failure.error == LurkError.Offline || failure.error == LurkError.Timeout)
        }

    @Test
    fun sendsRequestHeaders() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        send(Frame.Text(call.request.headers["X-Probe"].orEmpty()))
                    }
                }
            val connection = KtorWebSocketConnector(client).connect(url, mapOf("X-Probe" to "seen"))
            assertEquals("seen", withTimeout(TIMEOUT_MS) { connection.incoming.first() })
            connection.close()
        }

    @Test
    fun abruptDropSurfacesAsLurkException() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        send(Frame.Text("last"))
                        flush()
                        awaitCancellation()
                    }
                }
            val connection = KtorWebSocketConnector(client).connect(url, emptyMap())
            val received = mutableListOf<String>()
            val failure =
                assertFailsWith<LurkException> {
                    withTimeout(TIMEOUT_MS) {
                        connection.incoming.collect {
                            received += it
                            // Drop the server only once the frame has arrived, so delivery is not racing the stop.
                            server?.stop(0, 0)
                        }
                    }
                }
            assertEquals(listOf("last"), received)
            assertTrue(failure.error == LurkError.Offline || failure.error == LurkError.Timeout)
        }

    @Test
    fun sendAfterServerCloseThrowsLurkException() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
                    }
                }
            val connection = KtorWebSocketConnector(client).connect(url, emptyMap())
            withTimeout(TIMEOUT_MS) { connection.incoming.toList() }
            val failure = assertFailsWith<LurkException> { connection.send("late") }
            assertTrue(failure.error == LurkError.Offline || failure.error == LurkError.Timeout)
        }

    @Test
    fun sendRightAfterIncomingCompletesNeverSucceeds() =
        runBlocking {
            val url =
                startServer {
                    webSocket("/ws") {
                        close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
                    }
                }
            withTimeout(TOTAL_TIMEOUT_MS) {
                repeat(RACE_ITERATIONS) { round ->
                    val connection = KtorWebSocketConnector(client).connect(url, emptyMap())
                    connection.incoming.toList()
                    val failure =
                        assertFailsWith<LurkException>("send succeeded in round $round") {
                            connection.send("late")
                        }
                    assertTrue(failure.error == LurkError.Offline || failure.error == LurkError.Timeout)
                    connection.close()
                }
            }
        }
}

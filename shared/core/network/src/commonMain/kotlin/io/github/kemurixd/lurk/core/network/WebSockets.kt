package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One open WebSocket. [incoming] carries text frames only; it completes on a normal close and
 * throws [LurkException] when the connection fails, including a drop without a close frame
 * (reported as Offline). [send] throws [LurkException] (Offline) once the server has closed or
 * [incoming] has completed or failed; a frame is never queued onto a closing session.
 *
 * [incoming] supports a single collector: a second collector would split the frames with the
 * first. Cancelling the collector does not close the connection. The caller owns [close] and
 * should call it in a `finally` block.
 */
interface WebSocketConnection {
    val incoming: Flow<String>

    suspend fun send(text: String)

    /** Closes the connection. Safe to call more than once. */
    suspend fun close()
}

fun interface WebSocketConnector {
    /** Opens a connection, or throws [LurkException] when it cannot be made. */
    suspend fun connect(
        url: String,
        headers: Map<String, String>,
    ): WebSocketConnection
}

class KtorWebSocketConnector(
    private val client: HttpClient,
) : WebSocketConnector {
    override suspend fun connect(
        url: String,
        headers: Map<String, String>,
    ): WebSocketConnection {
        val session =
            asLurk {
                client.webSocketSession(url) {
                    headers.forEach { (name, value) -> header(name, value) }
                }
            }
        return KtorWebSocketConnection(session)
    }
}

/** Runs [block], turning transport failures into [LurkException]; cancellation passes through. */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> asLurk(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        // A session closed by the peer reports cancellation; only the caller's own cancellation propagates.
        currentCoroutineContext().ensureActive()
        throw LurkException(LurkError.Offline, e)
    } catch (e: LurkException) {
        throw e
    } catch (e: Exception) {
        throw LurkException(mapThrowable(e) ?: LurkError.Offline, e)
    }

private class KtorWebSocketConnection(
    private val session: WebSocketSession,
) : WebSocketConnection {
    // Set as soon as incoming ends, which happens before Ktor closes the outgoing side.
    @Volatile
    private var closed = false

    override val incoming: Flow<String> =
        session.incoming
            .receiveAsFlow()
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .catch { e ->
                if (e is CancellationException || e is LurkException) throw e
                throw LurkException(mapThrowable(e) ?: LurkError.Offline, e)
            }.onCompletion { cause ->
                // A collector that merely stops early does not mean the socket closed.
                if (cause !is CancellationException) closed = true
            }

    override suspend fun send(text: String) {
        if (closed || session.outgoing.isClosedForSend) throw LurkException(LurkError.Offline)
        asLurk { session.send(Frame.Text(text)) }
    }

    override suspend fun close() {
        try {
            session.close()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") ignored: Exception,
        ) {
            // Already closed or the peer is gone; closing twice must stay harmless.
        }
    }
}

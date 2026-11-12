package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.chat.ChatTransport
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val ANONYMOUS_NICK_PREFIX = "justinfan"
private const val MIN_NICK_DIGITS = 1_000
private const val MAX_NICK_DIGITS = 100_000
private const val PING_PREFIX = "PING "

/**
 * Twitch chat over IRC-on-WebSocket. Each collection opens one connection and sends the capability request, the
 * login (signed in only), a nick and the channel join; every frame the server sends is emitted as received, and each
 * `PING` line is answered. The flow completes when the socket closes. The token is read once per connection and never
 * kept, and this class is deliberately not a data class so it can't print it.
 */
class IrcTransport(
    private val connector: WebSocketConnector,
    private val config: StateFlow<RemoteConfig>,
    private val login: String,
    private val token: suspend () -> String?,
    private val random: Random = Random.Default,
) : ChatTransport {
    override fun frames(): Flow<String> =
        flow {
            val oauth = token()
            val connection = connector.connect(config.value.twitch.ircUrl, emptyMap())
            try {
                handshake(connection, oauth)
                connection.incoming.collect { frame ->
                    emit(frame)
                    frame.lineSequence().forEach { line ->
                        if (line.startsWith(PING_PREFIX)) {
                            connection.send("PONG ${line.removePrefix(PING_PREFIX).trimEnd('\r')}")
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) { connection.close() }
            }
        }

    private suspend fun handshake(
        connection: WebSocketConnection,
        oauth: String?,
    ) {
        connection.send(CAPABILITIES)
        if (oauth != null) connection.send("PASS oauth:$oauth")
        val nick =
            if (oauth !=
                null
            ) {
                login
            } else {
                "$ANONYMOUS_NICK_PREFIX${random.nextInt(MIN_NICK_DIGITS, MAX_NICK_DIGITS)}"
            }
        connection.send("NICK $nick")
        connection.send("JOIN #${login.lowercase()}")
    }
}

internal const val CAPABILITIES = "CAP REQ :twitch.tv/tags twitch.tv/commands"

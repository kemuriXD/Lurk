package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.chat.ChatTransport
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val NICK_PREFIX = "justinfan"
private const val MIN_NICK_DIGITS = 1_000
private const val MAX_NICK_DIGITS = 100_000
private const val PING_PREFIX = "PING "
private const val NOTICE_TO_ALL = " NOTICE * :"
internal const val CAPABILITIES = "CAP REQ :twitch.tv/tags twitch.tv/commands"

/** The notices Twitch sends, before hanging up, when it rejects the `PASS` token. */
private val LOGIN_FAILURES = listOf("Login authentication failed", "Improperly formatted auth")

/**
 * Twitch chat over IRC-on-WebSocket. Each collection opens one connection and sends the capability request, the
 * login (signed in only), a nick and the channel join; each `PING` line is answered, then every frame the server sends
 * is emitted as received. The flow completes when the socket closes. When Twitch rejects the token, the token is
 * handed to [onLoginRejected] and the flow completes without emitting the notice, so the next connection can go out
 * signed out. The credential is read once per connection and never kept, and this class is deliberately not a data
 * class so it can't print it.
 */
class IrcTransport(
    private val connector: WebSocketConnector,
    private val config: StateFlow<RemoteConfig>,
    private val login: String,
    private val credential: suspend () -> IrcCredential?,
    private val onLoginRejected: suspend (usedToken: String) -> Unit = {},
    private val random: Random = Random.Default,
) : ChatTransport {
    override fun frames(): Flow<String> =
        flow {
            val signIn = credential()
            val connection = connector.connect(config.value.twitch.ircUrl, emptyMap())
            try {
                handshake(connection, signIn)
                emitAll(
                    connection.incoming.transformWhile { frame ->
                        answerPings(connection, frame)
                        val rejected = signIn != null && isLoginFailure(frame)
                        if (rejected) onLoginRejected(signIn.token) else emit(frame)
                        !rejected
                    },
                )
            } finally {
                withContext(NonCancellable) { connection.close() }
            }
        }

    private suspend fun handshake(
        connection: WebSocketConnection,
        signIn: IrcCredential?,
    ) {
        connection.send(CAPABILITIES)
        if (signIn != null) connection.send("PASS oauth:${signIn.token}")
        connection.send("NICK ${ircNick(signIn, random)}")
        connection.send("JOIN #${login.lowercase()}")
    }
}

/**
 * The account login when signed in, else a justinfan-style nick. Twitch logs a `PASS` connection in as whoever the
 * nick names: a justinfan nick with a real token is still the anonymous user, and its messages are dropped.
 */
internal fun ircNick(
    signIn: IrcCredential?,
    random: Random,
): String = signIn?.login ?: "$NICK_PREFIX${random.nextInt(MIN_NICK_DIGITS, MAX_NICK_DIGITS)}"

/** True when a line of [frame] is Twitch's `NOTICE * :…` rejecting the login. */
private fun isLoginFailure(frame: String): Boolean =
    frame.lineSequence().any { line -> NOTICE_TO_ALL in line && LOGIN_FAILURES.any { it in line } }

/** Replies `PONG :x` to every `PING :x` line of [frame]. */
internal suspend fun answerPings(
    connection: WebSocketConnection,
    frame: String,
) {
    frame.lineSequence().forEach { line ->
        if (line.startsWith(PING_PREFIX)) {
            connection.send("PONG ${line.removePrefix(PING_PREFIX).trimEnd('\r')}")
        }
    }
}

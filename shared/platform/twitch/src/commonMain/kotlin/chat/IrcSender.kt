package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.chat.ChatSender
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Sends chat messages over one authenticated IRC connection, opened on the first send. A drain of the socket, run in
 * [scope], answers the server's PINGs and notices when the server hangs up; the next send then opens a fresh
 * connection. When a send finds the connection dead, it opens a fresh one and tries once more; a second failure is
 * returned, with no further retries. The token is read once per connection. Not a data class, so the token can't
 * leak through `toString()`.
 */
class IrcSender(
    private val connector: WebSocketConnector,
    private val config: StateFlow<RemoteConfig>,
    private val login: String,
    private val token: suspend () -> String?,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) : ChatSender {
    private class Held(
        val connection: WebSocketConnection,
    ) {
        @Volatile
        var dead = false
        var drain: Job? = null
    }

    private val lock = Mutex()
    private var held: Held? = null

    override suspend fun send(
        text: String,
        replyToId: String?,
    ): Outcome<Unit> =
        lock.withLock {
            outcomeOf {
                val line = privmsg(text, replyToId)
                try {
                    live().send(line)
                } catch (e: LurkException) {
                    if (e.error != LurkError.Offline) throw e
                    drop()
                    live().send(line)
                }
            }
        }

    /** The live connection; one the server has closed is dropped first, so the send goes to a fresh socket. */
    private suspend fun live(): WebSocketConnection {
        if (held?.dead == true) drop()
        val current = held ?: (open() ?: throw LurkException(LurkError.NotSignedIn)).also { held = it }
        return current.connection
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun open(): Held? {
        val oauth = token() ?: return null
        val opened = connector.connect(config.value.twitch.ircUrl, emptyMap())
        try {
            opened.send("PASS oauth:$oauth")
            opened.send("NICK ${ircNick(random)}")
        } catch (e: Throwable) {
            withContext(NonCancellable) { opened.close() }
            throw e
        }
        return Held(opened).also { it.drain = scope.launch { drain(it) } }
    }

    /** Reads the server's frames so its buffer never fills, answers PINGs, and marks the connection dead at the end. */
    private suspend fun drain(target: Held) {
        try {
            target.connection.incoming.collect { answerPings(target.connection, it) }
        } catch (
            @Suppress("SwallowedException") ignored: LurkException,
        ) {
            // The socket failed; the next send reconnects.
        } finally {
            target.dead = true
        }
    }

    private suspend fun drop() {
        val old = held ?: return
        held = null
        old.drain?.cancel()
        withContext(NonCancellable) { old.connection.close() }
    }

    private fun privmsg(
        text: String,
        replyToId: String?,
    ): String {
        val flat = text.replace("\r\n", "  ").replace('\r', ' ').replace('\n', ' ')
        val tag = if (replyToId != null) "@reply-parent-msg-id=$replyToId " else ""
        return "${tag}PRIVMSG #${login.lowercase()} :$flat"
    }
}

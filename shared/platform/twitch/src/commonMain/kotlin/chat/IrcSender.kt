package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.chat.ChatSender
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sends chat messages over one authenticated IRC connection, opened on the first send. When a send finds the
 * connection dead, it opens a fresh one and tries once more; a second failure is returned, with no further retries.
 * The token is read once per connection. Not a data class, so the token can't leak through `toString()`.
 */
class IrcSender(
    private val connector: WebSocketConnector,
    private val config: StateFlow<RemoteConfig>,
    private val login: String,
    private val token: suspend () -> String?,
) : ChatSender {
    private val lock = Mutex()
    private var connection: WebSocketConnection? = null

    override suspend fun send(
        text: String,
        replyToId: String?,
    ): Outcome<Unit> =
        lock.withLock {
            outcomeOf {
                val line = privmsg(text, replyToId)
                try {
                    sendOn(held() ?: throw LurkException(LurkError.NotSignedIn), line)
                } catch (e: LurkException) {
                    if (e.error != LurkError.Offline) throw e
                    drop()
                    sendOn(held() ?: throw LurkException(LurkError.NotSignedIn), line)
                }
            }
        }

    private suspend fun sendOn(
        target: WebSocketConnection,
        line: String,
    ) = target.send(line)

    private suspend fun held(): WebSocketConnection? = connection ?: open()?.also { connection = it }

    private suspend fun open(): WebSocketConnection? {
        val oauth = token() ?: return null
        val opened = connector.connect(config.value.twitch.ircUrl, emptyMap())
        try {
            opened.send("PASS oauth:$oauth")
            opened.send("NICK ${login.lowercase()}")
        } catch (e: LurkException) {
            withContext(NonCancellable) { opened.close() }
            throw e
        }
        return opened
    }

    private suspend fun drop() {
        val old = connection ?: return
        connection = null
        withContext(NonCancellable) { old.close() }
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

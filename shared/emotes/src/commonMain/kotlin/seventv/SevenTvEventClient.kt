package io.github.kemurixd.lurk.emotes.seventv

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.emotes.EmoteApi
import io.github.kemurixd.lurk.emotes.EmoteRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val EVENT_API_URL = "wss://events.7tv.io/v3"
private const val OP_RESUME = 34
private const val OP_SUBSCRIBE = 35
private const val MISSED_HEARTBEATS = 3
private const val RESUME = "RESUME"

/** Used for the liveness check until the server's Hello says otherwise. */
private val DEFAULT_HEARTBEAT = 30.seconds

private data class Subscription(
    val type: String,
    val condition: Map<String, String>,
) {
    fun frame(): String =
        buildJsonObject {
            put("op", OP_SUBSCRIBE)
            putJsonObject("d") {
                put("type", type)
                putJsonObject("condition") { condition.forEach { (key, value) -> put(key, value) } }
            }
        }.toString()
}

private fun resumeFrame(sessionId: String): String =
    buildJsonObject {
        put("op", OP_RESUME)
        putJsonObject("d") { put("session_id", sessionId) }
    }.toString()

/** A server session and the subscriptions it held when its connection ended. */
private class SessionRecord(
    val id: String,
    val subscriptions: Set<Subscription>,
)

/** A personal emote set and the users entitled to it; [emotes] is null until it has loaded. */
private class PersonalSet(
    val users: MutableSet<String> = mutableSetOf(),
    var emotes: List<Emote>? = null,
    var loading: Boolean = false,
)

/**
 * Follows the 7TV EventAPI for one channel: live changes to the channel's emote set, 7TV paints and badges, and
 * users' personal emote sets.
 *
 * Each connection waits for Hello. With no earlier session it subscribes to everything; otherwise it asks to resume
 * the earlier session and, on the answer, sends only the subscriptions that session did not hold (all of them when
 * the resume fails). Any message counts as liveness; three heartbeat intervals of silence end the connection.
 * Reconnect, EndOfStream, a closed or failed socket and a failed connect all lead to a new connection after
 * `backoff.delayFor(attempt)`. The attempt count resets once a session is established, meaning a heartbeat has
 * arrived after Hello, so a server that ends every stream right after Hello still meets a growing delay.
 */
class SevenTvEventClient(
    private val connector: WebSocketConnector,
    private val api: EmoteApi,
    private val registry: EmoteRegistry,
    private val cosmetics: CosmeticsRegistry,
    private val dispatchers: DispatcherProvider,
    private val backoff: Backoff = Backoff(base = 1.seconds, max = 30.seconds),
) {
    private val log = Logger.withTag("SevenTvEventClient")

    /** Runs until cancelled. [channelSetId] may start null; the set is subscribed once it is known. */
    suspend fun run(
        platform: Platform,
        channelId: String,
        channelSetId: StateFlow<String?>,
    ) {
        withContext(dispatchers.default) {
            val run = RunState(platform, channelId, channelSetId, this)
            var attempt = 0
            while (true) {
                if (connectOnce(run)) attempt = 0
                delay(backoff.delayFor(attempt))
                attempt++
            }
        }
    }

    /** One connection, from connect to its end; true when its session was established. Only cancellation escapes. */
    private suspend fun connectOnce(run: RunState): Boolean {
        val session = Session(run)
        var connection: WebSocketConnection? = null
        try {
            connection = connector.connect(EVENT_API_URL, emptyMap())
            session.serve(connection)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            log.i { "7TV EventAPI connection cancelled by the transport (${e::class.simpleName})" }
        } catch (e: LurkException) {
            log.i { "7TV EventAPI connection failed: ${e.error}" }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "7TV EventAPI connection failed unexpectedly" }
        } finally {
            connection?.let { withContext(NonCancellable) { it.close() } }
            session.record()?.let { run.lastSession = it }
        }
        return session.established
    }

    /** State that outlives a connection: the session to resume, what to subscribe to, and personal emote sets. */
    private inner class RunState(
        val platform: Platform,
        val channelId: String,
        val channelSetId: StateFlow<String?>,
        val scope: CoroutineScope,
    ) {
        var lastSession: SessionRecord? = null

        private val personalSetIds = MutableStateFlow<Set<String>>(emptySet())
        private val personalLock = Mutex()
        private val personalSets = mutableMapOf<String, PersonalSet>()
        private val userSets = mutableMapOf<String, String>()
        private val channelCondition =
            mapOf("ctx" to "channel", "platform" to platform.name, "id" to channelId)

        /** Emits whenever the wanted subscriptions may have changed. */
        val changes: Flow<Unit> = combine(channelSetId, personalSetIds) { _, _ -> }

        fun subscriptions(): List<Subscription> =
            (
                listOfNotNull(channelSetId.value) +
                    personalSetIds.value
            ).map { Subscription("emote_set.*", mapOf("object_id" to it)) }
                .plus(Subscription("cosmetic.*", channelCondition))
                .plus(Subscription("entitlement.*", channelCondition))
                .distinct()

        suspend fun apply(change: SevenTvChange) {
            cosmetics.apply(change)
            when (change) {
                is SevenTvChange.EmoteAdded -> {
                    if (change.setId == channelSetId.value) {
                        registry.addEmote(EmoteProvider.SEVENTV, EmoteScope.CHANNEL, change.emote)
                    }
                    updatePersonal(change.setId) { list ->
                        list.filterNot { it.id == change.emote.id } + change.emote.copy(scope = EmoteScope.PERSONAL)
                    }
                }

                is SevenTvChange.EmoteRemoved -> {
                    if (change.setId == channelSetId.value) {
                        registry.removeEmote(EmoteProvider.SEVENTV, EmoteScope.CHANNEL, change.emoteId)
                    }
                    updatePersonal(change.setId) { list -> list.filterNot { it.id == change.emoteId } }
                }

                is SevenTvChange.Entitled -> {
                    if (change.kind == KIND_EMOTE_SET) entitle(change.refId, change.userId)
                }

                is SevenTvChange.Unentitled -> {
                    if (change.kind == KIND_EMOTE_SET) unentitle(change.refId, change.userId)
                }

                is SevenTvChange.PaintDefined, is SevenTvChange.BadgeDefined -> {
                    Unit
                }
            }
        }

        private suspend fun updatePersonal(
            setId: String,
            edit: (List<Emote>) -> List<Emote>,
        ) = personalLock.withLock {
            val set = personalSets[setId]
            val updated = set?.emotes?.let(edit)
            if (set != null && updated != null) {
                set.emotes = updated
                set.users.forEach { registry.putPersonal(it, updated) }
            }
        }

        /** Gives [userId] the set's emotes, subscribes to the set and loads it if nobody has yet. */
        private suspend fun entitle(
            setId: String,
            userId: String,
        ) {
            val load =
                personalLock.withLock {
                    val previous = userSets.put(userId, setId)
                    if (previous != null && previous != setId) {
                        registry.removePersonal(userId)
                        detach(userId, previous)
                    }
                    val set = personalSets.getOrPut(setId) { PersonalSet() }
                    set.users += userId
                    set.emotes?.let { registry.putPersonal(userId, it) }
                    val start = set.emotes == null && !set.loading
                    if (start) set.loading = true
                    start
                }
            personalSetIds.update { it + setId }
            if (load) scope.launch { load(setId) }
        }

        private suspend fun unentitle(
            setId: String,
            userId: String,
        ) = personalLock.withLock {
            if (userSets[userId] == setId) {
                userSets -= userId
                registry.removePersonal(userId)
                detach(userId, setId)
            }
        }

        /** Call with [personalLock] held. A set nobody holds any more is forgotten and not subscribed again. */
        private fun detach(
            userId: String,
            setId: String,
        ) {
            val set = personalSets[setId] ?: return
            set.users -= userId
            if (set.users.isEmpty()) {
                personalSets -= setId
                personalSetIds.update { it - setId }
            }
        }

        private suspend fun load(setId: String) {
            val emotes =
                try {
                    api.sevenTvSet(setId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LurkException) {
                    log.w { "Could not load a 7TV personal emote set: ${e.error}" }
                    null
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    log.w(e) { "Could not load a 7TV personal emote set" }
                    null
                }
            personalLock.withLock {
                val set = personalSets[setId]
                if (set != null) {
                    set.loading = false
                    if (emotes != null) {
                        set.emotes = emotes
                        set.users.forEach { registry.putPersonal(it, emotes) }
                    }
                }
            }
        }
    }

    /** One connection's session. [sent] and [ready] are shared by the reader and the subscriber, under [lock]. */
    private inner class Session(
        private val run: RunState,
    ) {
        private val lock = Mutex()
        private val sent = mutableSetOf<Subscription>()
        private var ready = false
        private var resuming: SessionRecord? = null
        private var sessionId: String? = null
        private var heartbeat: Duration = DEFAULT_HEARTBEAT

        var established = false
            private set

        /** The session as it stands, or null when no Hello arrived. Read only after [serve] has returned. */
        fun record(): SessionRecord? = sessionId?.let { SessionRecord(it, sent.toSet()) }

        suspend fun serve(connection: WebSocketConnection) =
            coroutineScope {
                val inbox = Channel<String>(Channel.UNLIMITED)
                launch { pump(connection, inbox) }
                launch { run.changes.collect { sync(connection) } }
                read(connection, inbox)
                coroutineContext.cancelChildren()
            }

        /** Moves frames into [inbox]; the end of the stream, normal or not, closes it. */
        private suspend fun pump(
            connection: WebSocketConnection,
            inbox: Channel<String>,
        ) {
            try {
                connection.incoming.collect { inbox.send(it) }
                inbox.close()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                inbox.close(e)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                inbox.close(e)
            }
        }

        private suspend fun read(
            connection: WebSocketConnection,
            inbox: ReceiveChannel<String>,
        ) {
            var open = true
            while (open) {
                val text = receive(inbox)
                open = text != null && handle(connection, EventApiParser.parse(text))
            }
        }

        /** The next frame, or null when the stream ended or went quiet for [MISSED_HEARTBEATS] intervals. */
        private suspend fun receive(inbox: ReceiveChannel<String>): String? {
            val result = withTimeoutOrNull(heartbeat * MISSED_HEARTBEATS) { inbox.receiveCatching() }
            when {
                result == null -> {
                    log.i { "7TV EventAPI went quiet for ${heartbeat * MISSED_HEARTBEATS}" }
                }

                result.isClosed -> {
                    log.i {
                        "7TV EventAPI stream ended (${result.exceptionOrNull()?.let {
                            it::class
                                .simpleName
                        }})"
                    }
                }
            }
            return result?.getOrNull()
        }

        /** Returns false when the connection should end. */
        private suspend fun handle(
            connection: WebSocketConnection,
            message: EventApiMessage,
        ): Boolean {
            when (message) {
                is EventApiMessage.Hello -> hello(connection, message)
                is EventApiMessage.Heartbeat -> heartbeat(connection)
                is EventApiMessage.Ack -> ack(connection, message)
                is EventApiMessage.Dispatch -> SevenTvDispatch.changes(message, run.platform).forEach { run.apply(it) }
                is EventApiMessage.Reconnect -> log.i { "7TV EventAPI asked for a reconnect" }
                is EventApiMessage.EndOfStream -> log.i { "7TV EventAPI end of stream ${message.code}" }
                is EventApiMessage.Unknown -> Unit
            }
            return message !is EventApiMessage.Reconnect && message !is EventApiMessage.EndOfStream
        }

        private suspend fun hello(
            connection: WebSocketConnection,
            hello: EventApiMessage.Hello,
        ) {
            if (sessionId != null) return
            sessionId = hello.sessionId
            heartbeat = hello.heartbeatInterval
            val previous = run.lastSession
            lock.withLock {
                if (previous != null) {
                    connection.send(resumeFrame(previous.id))
                    resuming = previous
                } else {
                    ready = true
                }
            }
            sync(connection)
        }

        /**
         * A heartbeat after Hello means the session is established. A resume still unanswered a whole heartbeat
         * interval later counts as failed, so the session is never left without subscriptions.
         */
        private suspend fun heartbeat(connection: WebSocketConnection) {
            if (sessionId != null) {
                established = true
                finishResume(connection, succeeded = false)
            }
        }

        private suspend fun ack(
            connection: WebSocketConnection,
            ack: EventApiMessage.Ack,
        ) {
            if (ack.command == RESUME) finishResume(connection, succeeded = ack.success == true)
        }

        /** Ends a pending resume: keep what the old session held when it succeeded, then send whatever is missing. */
        private suspend fun finishResume(
            connection: WebSocketConnection,
            succeeded: Boolean,
        ) {
            val finished =
                lock.withLock {
                    val previous = resuming
                    if (previous != null) {
                        if (succeeded) sent += previous.subscriptions
                        resuming = null
                        ready = true
                    }
                    previous != null
                }
            if (finished) sync(connection)
        }

        private suspend fun sync(connection: WebSocketConnection) =
            lock.withLock {
                if (ready) {
                    for (subscription in run.subscriptions()) {
                        if (subscription !in sent) {
                            connection.send(subscription.frame())
                            sent += subscription
                        }
                    }
                }
            }
    }
}

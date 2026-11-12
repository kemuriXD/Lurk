package io.github.kemurixd.lurk.chat

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.HighlightType
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.emotes.EmoteSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * One joined chat room: raw transport frames in, an immutable [ChatSnapshot] out.
 *
 * [run] starts four coroutines on `dispatchers.default`. The connection loop collects [transport] one connection at a
 * time, parses frames, batches events for [batchInterval] and handles reconnects. Two forwarders pass on [emotes] and
 * [preferences] changes. All of them only send inputs to one channel, and a single consumer of that channel owns every
 * piece of mutable state (the ring buffer, the seen ids, the pinned item, the reconnect attempt, the newest
 * timestamp) and is the only writer of [state]. Nothing is shared, so nothing needs a lock.
 *
 * Messages change [state] at most once per batch; connection changes are published at once.
 */
@Suppress("LongParameterList")
class ChatSession(
    private val channel: ChatChannel,
    private val transport: ChatTransport,
    private val history: ChatHistorySource?,
    private val emotes: StateFlow<EmoteSnapshot>,
    private val preferences: StateFlow<ChatPreferences>,
    private val selfLogin: String?,
    private val tokenizer: Tokenizer,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val backoff: Backoff = Backoff(base = 1.seconds, max = 30.seconds),
    private val batchInterval: Duration = 1.seconds,
) {
    private val log = Logger.withTag("ChatSession")
    private val frameParser = ChatParsers.frames(channel.platform)
    private val historyParser = ChatParsers.history(channel.platform)
    private val mutableState = MutableStateFlow(ChatSnapshot(emptyList(), null, ConnectionState.CONNECTING))

    val state: StateFlow<ChatSnapshot> = mutableState.asStateFlow()

    /** Runs the session until cancelled. Call it once per session. */
    suspend fun run() {
        withContext(dispatchers.default) {
            val inputs = Channel<Input>(Channel.UNLIMITED)
            // A signal only: the room reads the latest snapshot, so a burst costs one re-tokenize pass.
            launch { emotes.collect { inputs.send(Input.EmotesChanged) } }
            launch { preferences.collect { inputs.send(Input.Preferences(it)) } }
            launch { connectLoop(inputs) }
            val room = ChatRoom(ItemRules(emotes.value, preferences.value, selfLogin, tokenizer))
            for (input in inputs) room.handle(input)
        }
    }

    private suspend fun connectLoop(inputs: SendChannel<Input>) {
        if (wantsHistory()) fetchHistory()?.let { inputs.send(Input.History(it, backfill = false)) }
        while (true) {
            if (collectConnection(inputs)) {
                inputs.send(Input.ReconnectRequested)
            } else {
                val retryDelay = CompletableDeferred<Duration>()
                inputs.send(Input.Dropped(retryDelay))
                delay(retryDelay.await())
            }
            if (channel.platform == Platform.TWITCH && wantsHistory()) {
                fetchHistory()?.let { inputs.send(Input.History(it, backfill = true)) }
            }
        }
    }

    /**
     * Collects one connection until it drops. Returns true when the server asked for a reconnect. Pending events are
     * always sent before this returns, so nothing from the old connection is lost or reordered.
     */
    private suspend fun collectConnection(inputs: SendChannel<Input>): Boolean {
        var reconnectRequested = false
        try {
            flow {
                var first = true
                transport.frames().collect { frame ->
                    if (first) {
                        first = false
                        inputs.send(Input.Connected)
                    }
                    emit(frame)
                }
            }.transformWhile { frame ->
                val events = frameParser.parse(frame)
                val reconnectAt = events.indexOfFirst { it is ChatEvent.ReconnectRequested }
                val kept = if (reconnectAt < 0) events else events.subList(0, reconnectAt + 1)
                kept.forEach { emit(it) }
                reconnectAt < 0
            }.batched(batchInterval)
                .collect { batch ->
                    if (batch.any { it is ChatEvent.ReconnectRequested }) reconnectRequested = true
                    inputs.send(Input.Batch(batch))
                }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            log.i { "Chat connection cancelled by the transport: ${e.message}" }
        } catch (e: LurkException) {
            log.i { "Chat connection dropped: ${e.error}" }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "Chat connection dropped unexpectedly" }
        }
        return reconnectRequested
    }

    private fun wantsHistory(): Boolean = history != null && preferences.value.recentMessages

    /**
     * The parsed history, or null when the fetch failed, timed out after [HISTORY_TIMEOUT] or was cancelled by the
     * source itself. Live chat waits for it, so none of these may end the connection loop.
     */
    private suspend fun fetchHistory(): List<ChatEvent>? {
        val source = history ?: return null
        return try {
            val body = withTimeoutOrNull(HISTORY_TIMEOUT) { source.fetch() }
            if (body == null) log.w { "Chat history timed out" }
            body?.let(historyParser)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            log.w { "Chat history cancelled by the source (${e::class.simpleName})" }
            null
        } catch (e: LurkException) {
            log.w { "Chat history failed: ${e.error}" }
            null
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w(e) { "Chat history failed unexpectedly" }
            null
        }
    }

    private sealed interface Input {
        class Batch(
            val events: List<ChatEvent>,
        ) : Input

        /** History from [ChatHistorySource]; a [backfill] follows a reconnect and adds only what was missed. */
        class History(
            val events: List<ChatEvent>,
            val backfill: Boolean,
        ) : Input

        /** The first frame of a connection arrived. */
        data object Connected : Input

        /** The connection dropped; the room answers with how long to wait before reconnecting. */
        class Dropped(
            val retryDelay: CompletableDeferred<Duration>,
        ) : Input

        /** The server asked for a reconnect, which happens at once and is not a failure. */
        data object ReconnectRequested : Input

        /** [emotes] changed; the room reads its current value. */
        data object EmotesChanged : Input

        class Preferences(
            val value: ChatPreferences,
        ) : Input
    }

    /** The session's mutable state. Only the coroutine that consumes the input channel touches it. */
    private inner class ChatRoom(
        private var rules: ItemRules,
    ) {
        private val buffer = RingBuffer<ChatItem>(BUFFER_CAPACITY)

        /** Ids of the buffered items; evicted and removed ids leave it, so it never outgrows the buffer. */
        private val seen = HashSet<String>()
        private var pinned: ChatItem? = null
        private var connection = ConnectionState.CONNECTING
        private var attempt = 0
        private var newestSentAt: Instant? = null
        private var systemLines = 0

        /** True once this outage has its reconnecting line; the next first frame ends the outage. */
        private var outageAnnounced = false

        fun handle(input: Input) {
            when (input) {
                is Input.Batch -> {
                    if (apply(input.events)) publish()
                }

                is Input.History -> {
                    val events = if (input.backfill) missedSince(input.events) else input.events
                    if (apply(events)) publish()
                }

                Input.Connected -> {
                    attempt = 0
                    outageAnnounced = false
                    setConnection(ConnectionState.CONNECTED)
                }

                is Input.Dropped -> {
                    // One line per outage: failed retries inside it add nothing and publish nothing.
                    val announce = !outageAnnounced
                    if (announce) {
                        outageAnnounced = true
                        append(listOf(reconnectingLine()))
                    }
                    if (announce || connection != ConnectionState.RECONNECTING) {
                        connection = ConnectionState.RECONNECTING
                        publish()
                    }
                    input.retryDelay.complete(backoff.delayFor(attempt))
                    attempt++
                }

                Input.ReconnectRequested -> {
                    setConnection(ConnectionState.RECONNECTING)
                }

                Input.EmotesChanged -> {
                    onEmotes(emotes.value)
                }

                is Input.Preferences -> {
                    onPreferences(input.value)
                }
            }
        }

        private fun publish() {
            mutableState.value = ChatSnapshot(buffer.toList(), pinned, connection)
        }

        private fun setConnection(next: ConnectionState) {
            if (connection == next) return
            connection = next
            publish()
        }

        /** Applies events in order. Returns true when anything visible changed. */
        private fun apply(events: List<ChatEvent>): Boolean {
            val batch = Batch()
            var pinChanged = false
            for (event in events) {
                when (event) {
                    is ChatEvent.Message -> {
                        batch.add(event.message)
                    }

                    is ChatEvent.Removed -> {
                        batch.remove(event.messageId)
                    }

                    is ChatEvent.UserCleared -> {
                        batch.clearUser(event.userId)
                    }

                    is ChatEvent.Pinned -> {
                        pinned = rules.pinned(event.message)
                        pinChanged = true
                    }

                    is ChatEvent.Unpinned -> {
                        pinChanged = unpin(event.messageId) || pinChanged
                    }

                    ChatEvent.ChatCleared, is ChatEvent.RoomState, ChatEvent.ReconnectRequested -> {
                        // No change in v1. History may carry a RECONNECT line; only the live connection acts on
                        // one, in collectConnection.
                    }
                }
            }
            val marked = batch.markBuffered()
            append(batch.added)
            return batch.added.isNotEmpty() || marked || pinChanged
        }

        private fun append(items: List<ChatItem>) {
            if (items.isEmpty()) return
            items.forEach { seen += it.message.id }
            buffer.addAll(items).forEach { seen -= it.message.id }
        }

        /**
         * Back-fill keeps messages from the newest one shown onwards, oldest first. The same millisecond counts, since
         * two messages can share one; [apply] drops the ids already seen.
         */
        private fun missedSince(events: List<ChatEvent>): List<ChatEvent> {
            val since = newestSentAt
            return events
                .filterIsInstance<ChatEvent.Message>()
                .filter { since == null || it.message.sentAt >= since }
                .sortedBy { it.message.sentAt }
        }

        /** Clears the pin when [messageId] is null (the platform did not name one) or names the pinned message. */
        private fun unpin(messageId: String?): Boolean {
            val current = pinned
            val matches = current != null && (messageId == null || messageId == current.message.id)
            if (matches) pinned = null
            return matches
        }

        private fun onEmotes(snapshot: EmoteSnapshot) {
            // Signals queued behind one that already applied the latest snapshot have nothing left to do.
            if (snapshot === rules.emotes) return
            rules = ItemRules(snapshot, rules.prefs, selfLogin, tokenizer)
            var changed = false
            // Any other generation means the parts were built from a different emote set.
            buffer.replaceAll { item ->
                if (item.emoteGeneration == snapshot.generation) {
                    item
                } else {
                    changed = true
                    rules.retokenized(item)
                }
            }
            pinned?.takeIf { it.emoteGeneration != snapshot.generation }?.let {
                pinned = rules.pinned(it.message)
                changed = true
            }
            if (changed) publish()
        }

        private fun onPreferences(next: ChatPreferences) {
            val previous = rules.prefs
            if (next == previous) return
            rules = ItemRules(rules.emotes, next, selfLogin, tokenizer)
            var changed = false
            if (next.blockedUsers != previous.blockedUsers || next.blockedWords != previous.blockedWords) {
                buffer.removeIf { item ->
                    rules.isBlocked(item.message).also { blocked ->
                        if (blocked) {
                            seen -= item.message.id
                            changed = true
                        }
                    }
                }
            }
            val retokenize = next.ffzEffects != previous.ffzEffects
            if (retokenize || next.highlights != previous.highlights) {
                buffer.replaceAll { if (retokenize) rules.retokenized(it) else rules.reclassified(it) }
                pinned = pinned?.let { rules.pinned(it.message) }
                changed = true
            }
            if (changed) publish()
        }

        private fun reconnectingLine(): ChatItem {
            systemLines++
            val message =
                ChatMessage(
                    id = "system:reconnecting:$systemLines",
                    platform = channel.platform,
                    channelId = channel.channelId,
                    sender = null,
                    text = "",
                    sentAt = clock.now(),
                    kind = MessageKind.SYSTEM,
                    tags = mapOf(SYSTEM_TAG to RECONNECTING),
                )
            return ChatItem(message, emptyList(), highlight = null, emoteGeneration = rules.emotes.generation)
        }

        /** The new items of one batch, plus the removals that still have to reach the buffer. */
        private inner class Batch {
            val added = ArrayList<ChatItem>()
            private val addedAt = HashMap<String, Int>()
            private val removedIds = HashSet<String>()
            private val clearedUsers = HashSet<String>()

            fun add(message: ChatMessage) {
                if (message.id in seen || message.id in addedAt || rules.isBlocked(message)) return
                addedAt[message.id] = added.size
                added += rules.item(message)
                newestSentAt = newestSentAt?.let { maxOf(it, message.sentAt) } ?: message.sentAt
            }

            fun remove(messageId: String) {
                removedIds += messageId
                addedAt[messageId]?.let { added[it] = added[it].copy(deleted = true) }
            }

            /** Marks the user's messages so far; later ones in the same batch stay. */
            fun clearUser(userId: String) {
                clearedUsers += userId
                for (i in added.indices) {
                    if (added[i].message.sender?.id == userId) added[i] = added[i].copy(deleted = true)
                }
            }

            /** One pass over the buffer for every removal in the batch. Returns true when an item changed. */
            fun markBuffered(): Boolean {
                if (removedIds.isEmpty() && clearedUsers.isEmpty()) return false
                var changed = false
                buffer.replaceAll { item ->
                    val hit = item.message.id in removedIds || item.message.sender?.id in clearedUsers
                    if (hit && !item.deleted) {
                        changed = true
                        item.copy(deleted = true)
                    } else {
                        item
                    }
                }
                return changed
            }
        }
    }

    private companion object {
        const val BUFFER_CAPACITY = 1000
        val HISTORY_TIMEOUT = 10.seconds
        const val SYSTEM_TAG = "system"
        const val RECONNECTING = "reconnecting"
    }
}

/**
 * How messages become [ChatItem]s under one emote snapshot and one set of preferences. Immutable; the session builds a
 * new one when either changes.
 */
private class ItemRules(
    val emotes: EmoteSnapshot,
    val prefs: ChatPreferences,
    private val selfLogin: String?,
    private val tokenizer: Tokenizer,
) {
    private val blockedUsers = lowercased(prefs.blockedUsers)
    private val blockedWords = lowercased(prefs.blockedWords.map(::bareWord))

    /** A blocked login, or a blocked word anywhere in the text (whole words, ignoring case and punctuation). */
    fun isBlocked(message: ChatMessage): Boolean {
        val login = message.sender?.login
        val byUser = login != null && login.lowercase() in blockedUsers
        return byUser ||
            (blockedWords.isNotEmpty() && message.text.split(' ').any { bareWord(it).lowercase() in blockedWords })
    }

    fun item(message: ChatMessage): ChatItem {
        val parts = tokenizer.tokenize(message, emotes, prefs.ffzEffects)
        return ChatItem(message, parts, classify(message, parts), emoteGeneration = emotes.generation)
    }

    fun retokenized(item: ChatItem): ChatItem = item(item.message).copy(deleted = item.deleted)

    fun reclassified(item: ChatItem): ChatItem = item.copy(highlight = classify(item.message, item.parts))

    /** The pinned message, marked [HighlightType.KICK_PINNED] when that highlight is on. */
    fun pinned(message: ChatMessage): ChatItem {
        val item = item(message)
        return if (HighlightType.KICK_PINNED in prefs.highlights) {
            item.copy(highlight = Highlight(HighlightType.KICK_PINNED))
        } else {
            item
        }
    }

    private fun classify(
        message: ChatMessage,
        parts: List<MessagePart>,
    ): Highlight? = HighlightClassifier.classify(message, parts, prefs.highlights, selfLogin)
}

private fun lowercased(values: Iterable<String>): Set<String> =
    values.mapNotNullTo(HashSet()) { value -> value.lowercase().takeIf { it.isNotBlank() } }

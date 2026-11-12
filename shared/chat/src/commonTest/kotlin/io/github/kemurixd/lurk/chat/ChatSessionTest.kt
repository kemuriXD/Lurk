package io.github.kemurixd.lurk.chat

import app.cash.turbine.test
import io.github.kemurixd.lurk.chat.twitch.ircLine
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.HighlightType
import io.github.kemurixd.lurk.core.model.MessageKind
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.emotes.EmoteRegistry
import io.github.kemurixd.lurk.emotes.ProviderSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val ROOM_ID = "1004839"
private const val CHANNEL_LOGIN = "user_5700"
private const val CLOCK_BASE_MS = 1_794_080_000_000L
private const val TS_BASE_MS = 1_794_070_000_000L

/** A Twitch message id from the fixture files, by its last four hex digits. */
private fun tid(suffix: String) = "10000000-0000-4000-8000-00000000$suffix"

/** A PRIVMSG line with id `m-<n>`, sent [n] seconds after a fixed base unless [sentAtMs] says otherwise. */
private fun privmsg(
    n: Int,
    text: String = "message $n",
    login: String = "user_a",
    userId: String = "uid-$login",
    sentAtMs: Long = TS_BASE_MS + n * 1000L,
): String =
    "@badges=;color=#FFFFFF;display-name=$login;emotes=;first-msg=0;id=m-$n;mod=0;room-id=$ROOM_ID;" +
        "tmi-sent-ts=$sentAtMs;user-id=$userId;user-type= :$login!$login@$login.tmi.twitch.tv " +
        "PRIVMSG #$CHANNEL_LOGIN :$text"

private fun clearMsg(messageId: String) =
    "@login=user_a;room-id=$ROOM_ID;target-msg-id=$messageId;tmi-sent-ts=$TS_BASE_MS " +
        ":tmi.twitch.tv CLEARMSG #$CHANNEL_LOGIN :gone"

private const val RECONNECT = ":tmi.twitch.tv RECONNECT"

private fun recentMessages(vararg lines: String): String =
    buildJsonObject {
        put("messages", JsonArray(lines.map(::JsonPrimitive)))
        put("error", null as String?)
    }.toString()

private fun kickFrame(line: Int): String = fixture("kick/frames.jsonl").lines()[line - 1]

private val KEKW =
    Emote(
        id = "id-KEKW",
        name = "KEKW",
        provider = EmoteProvider.SEVENTV,
        scope = EmoteScope.CHANNEL,
        images = listOf(EmoteImage(1, "https://cdn.example/KEKW/1x")),
    )

private class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TestClock(
    private val scheduler: TestCoroutineScheduler,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(CLOCK_BASE_MS + scheduler.currentTime)
}

/** One collection of [FakeTransport.frames]: the test feeds [frames] and closes it to drop the connection. */
private class FakeConnection(
    val openedAt: Long,
) {
    val frames = Channel<String>(Channel.UNLIMITED)

    /** True once the session stopped collecting this connection. */
    var closed = false

    fun send(vararg lines: String) = lines.forEach { check(frames.trySend(it).isSuccess) { "connection closed" } }

    fun drop(cause: Throwable? = null) {
        frames.close(cause)
    }
}

private class FakeTransport(
    private val now: () -> Long,
) : ChatTransport {
    val connections = mutableListOf<FakeConnection>()
    var failure: (() -> Throwable)? = null

    val last: FakeConnection get() = connections.last()

    override fun frames(): Flow<String> =
        flow {
            val connection = FakeConnection(now())
            connections += connection
            failure?.let { throw it() }
            try {
                emitAll(connection.frames.consumeAsFlow())
            } finally {
                connection.closed = true
            }
        }
}

private class FakeHistory(
    vararg responses: () -> String,
) : ChatHistorySource {
    private val queue = ArrayDeque(responses.toList())
    var calls = 0

    override suspend fun fetch(): String {
        calls++
        return queue.removeFirstOrNull()?.invoke() ?: recentMessages()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class Rig(
    private val scope: TestScope,
    platform: Platform = Platform.TWITCH,
    history: ChatHistorySource? = null,
    preferences: ChatPreferences = ChatPreferences(),
    selfLogin: String? = null,
) {
    val transport = FakeTransport { scope.testScheduler.currentTime }
    val registry = EmoteRegistry()
    val prefs = MutableStateFlow(preferences)
    val emissions = mutableListOf<ChatSnapshot>()
    val session =
        ChatSession(
            channel = ChatChannel(platform, ROOM_ID, CHANNEL_LOGIN),
            transport = transport,
            history = history,
            emotes = registry.snapshot,
            preferences = prefs,
            selfLogin = selfLogin,
            tokenizer = Tokenizer(),
            dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
            clock = TestClock(scope.testScheduler),
            backoff = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1)),
        )

    val state: ChatSnapshot get() = session.state.value
    val ids: List<String> get() = state.items.map { it.message.id }

    /** Starts the session, recording every distinct `state` value. */
    fun start(record: Boolean = true): Rig {
        if (record) {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                session.state.collect { emissions += it }
            }
        }
        scope.backgroundScope.launch { session.run() }
        scope.runCurrent()
        return this
    }

    /** Lets the pending batch reach the ring buffer. */
    fun flush() {
        scope.advanceTimeBy(1.seconds)
        scope.runCurrent()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
    @Test
    fun historyThenLive() =
        runTest {
            val rig = Rig(this, history = FakeHistory({ fixture("twitch/recent-messages.json") })).start()
            val historyIds = listOf(tid("a3ee"), tid("a429"), tid("aaca"), tid("a434"), tid("a430"))
            assertEquals(historyIds, rig.ids, "history lands before any live frame")
            assertEquals(ConnectionState.CONNECTING, rig.state.connection)

            rig.transport.last.send(ircLine(5), ircLine(8), ircLine(10))
            runCurrent()
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
            rig.flush()

            assertEquals(historyIds + listOf(tid("a3eb"), tid("a44f"), tid("aa21")), rig.ids)
            assertEquals(List(5) { true } + List(3) { false }, rig.state.items.map { it.message.historical })
        }

    @Test
    fun batchesOncePerSecond() =
        runTest {
            val rig = Rig(this).start(record = false)
            val connection = rig.transport.last
            rig.session.state.test {
                assertEquals(ConnectionState.CONNECTING, awaitItem().connection)
                connection.send(privmsg(0))
                runCurrent()
                val connected = awaitItem()
                assertEquals(ConnectionState.CONNECTED, connected.connection)
                assertTrue(connected.items.isEmpty())
                for (n in 1 until 30) {
                    advanceTimeBy(30.milliseconds)
                    connection.send(privmsg(n))
                    runCurrent()
                }
                assertEquals(870, currentTime)
                expectNoEvents()

                advanceTimeBy(130.milliseconds)
                runCurrent()
                val batch = awaitItem()
                assertEquals(1000, currentTime)
                assertEquals((0 until 30).map { "m-$it" }, batch.items.map { it.message.id })
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun duplicateIdsDropped() =
        runTest {
            val rig = Rig(this, history = FakeHistory({ fixture("twitch/recent-messages.json") })).start()
            // Line 6 repeats a history message; line 5 comes twice in one batch and again in the next.
            rig.transport.last.send(ircLine(6), ircLine(5), ircLine(5))
            rig.flush()
            rig.transport.last.send(ircLine(5), ircLine(8))
            rig.flush()

            assertEquals(
                listOf(tid("a3ee"), tid("a429"), tid("aaca"), tid("a434"), tid("a430"), tid("a3eb"), tid("a44f")),
                rig.ids,
            )
        }

    @Test
    fun blockedUserAndWordFiltered() =
        runTest {
            val prefs = ChatPreferences(blockedUsers = setOf("user_bad"), blockedWords = setOf("Spoiler"))
            val rig = Rig(this, preferences = prefs).start()
            rig.transport.last.send(
                privmsg(1, "hello", login = "User_Bad"),
                privmsg(2, "this is a SPOILER!"),
                privmsg(3, "spoilers ahead"),
                privmsg(4, "(spoiler) here"),
                privmsg(5, "fine message"),
            )
            rig.flush()

            assertEquals(listOf("m-3", "m-5"), rig.ids)
        }

    @Test
    fun blockingLaterRemovesExisting() =
        runTest {
            val rig = Rig(this).start()
            rig.transport.last.send(
                privmsg(1, "hello", login = "user_a"),
                privmsg(2, "the spoiler is out", login = "user_b"),
                privmsg(3, "ok", login = "user_c"),
            )
            rig.flush()
            assertEquals(listOf("m-1", "m-2", "m-3"), rig.ids)
            val before = rig.emissions.size

            rig.prefs.value = ChatPreferences(blockedUsers = setOf("user_a"), blockedWords = setOf("spoiler"))
            runCurrent()

            assertEquals(listOf("m-3"), rig.ids)
            assertEquals(before + 1, rig.emissions.size, "one emission for the change")

            rig.transport.last.send(privmsg(4, "again", login = "user_a"))
            rig.flush()
            assertEquals(listOf("m-3"), rig.ids)
        }

    @Test
    fun removedMarksDeleted() =
        runTest {
            val rig = Rig(this).start()
            rig.transport.last.send(ircLine(5), ircLine(8))
            rig.flush()
            // Line 24 is the CLEARMSG for line 5.
            rig.transport.last.send(ircLine(24))
            rig.flush()
            // A removal in the same batch as its message.
            rig.transport.last.send(privmsg(1) + "\r\n" + clearMsg("m-1"))
            rig.flush()

            assertEquals(
                listOf(tid("a3eb") to true, tid("a44f") to false, "m-1" to true),
                rig.state.items.map { it.message.id to it.deleted },
            )
        }

    @Test
    fun userClearedMarksAll() =
        runTest {
            val rig = Rig(this).start()
            // Line 5 and m-2 come from user 1004840, the target of the CLEARCHAT on line 21.
            rig.transport.last.send(ircLine(5), privmsg(2, userId = "1004840", login = "user_5701"), ircLine(8))
            rig.flush()
            rig.transport.last.send(ircLine(21))
            rig.flush()
            // A whole-chat clear changes nothing in v1.
            rig.transport.last.send(ircLine(23))
            rig.flush()

            assertEquals(
                listOf(tid("a3eb") to true, "m-2" to true, tid("a44f") to false),
                rig.state.items.map { it.message.id to it.deleted },
            )
        }

    @Test
    fun reconnectBacksOff() =
        runTest {
            val rig = Rig(this)
            rig.transport.failure = { IllegalStateException("connection refused") }
            rig.start()

            val expected = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1))
            val opens = (0 until 6).map { expected.delayFor(it).inWholeMilliseconds }.runningFold(0L, Long::plus)
            advanceTimeBy(opens.last())
            runCurrent()

            assertEquals(opens, rig.transport.connections.map { it.openedAt }, "connect times follow the backoff")
            assertEquals(ConnectionState.RECONNECTING, rig.state.connection)
            assertEquals((1..7).map { "system:reconnecting:$it" }, rig.ids, "one line per drop")
            rig.state.items.forEach {
                assertEquals(MessageKind.SYSTEM, it.message.kind)
                assertEquals("reconnecting", it.message.tags["system"])
                assertNull(it.message.sender)
            }
            val firstDrop = rig.emissions[1]
            assertEquals(ConnectionState.RECONNECTING, firstDrop.connection)
            assertEquals(listOf("system:reconnecting:1"), firstDrop.items.map { it.message.id })
            assertEquals(
                Instant.fromEpochMilliseconds(CLOCK_BASE_MS),
                firstDrop.items
                    .single()
                    .message.sentAt,
            )
        }

    @Test
    fun attemptResetsOnFirstFrame() =
        runTest {
            val rig = Rig(this)
            rig.transport.failure = { IllegalStateException("connection refused") }
            rig.start()
            val expected = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1))
            var at = 0L
            for (attempt in 0 until 4) {
                at += expected.delayFor(attempt).inWholeMilliseconds
                if (attempt == 3) rig.transport.failure = null
                advanceTimeBy(at - currentTime)
                runCurrent()
            }
            assertEquals(5, rig.transport.connections.size)
            assertEquals(at, rig.transport.last.openedAt)

            rig.transport.last.send(privmsg(1))
            runCurrent()
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
            rig.transport.last.drop()
            runCurrent()
            // The first frame reset the attempt, so the wait is drawn from the attempt-0 range again.
            val retry = expected.delayFor(0).inWholeMilliseconds
            advanceTimeBy(retry)
            runCurrent()
            assertEquals(6, rig.transport.connections.size)
            assertEquals(at + retry, rig.transport.last.openedAt)
        }

    @Test
    fun backfillDedupes() =
        runTest {
            val history =
                FakeHistory(
                    { recentMessages(privmsg(0)) },
                    // Three messages already seen and two new ones, out of order. m-9 was never seen, but it is
                    // older than the last live message, so it is not part of the gap.
                    {
                        recentMessages(
                            privmsg(1),
                            privmsg(2),
                            privmsg(9, sentAtMs = TS_BASE_MS + 2500),
                            privmsg(5),
                            privmsg(3),
                            privmsg(4),
                        )
                    },
                )
            val rig = Rig(this, history = history).start()
            rig.transport.last.send(privmsg(1), privmsg(2), privmsg(3))
            rig.flush()

            rig.transport.last.drop()
            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(2, rig.transport.connections.size)
            assertEquals(2, history.calls)
            rig.transport.last.send(privmsg(6))
            rig.flush()

            assertEquals(
                listOf("m-0", "m-1", "m-2", "m-3", "system:reconnecting:1", "m-4", "m-5", "m-6"),
                rig.ids,
            )
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
        }

    @Test
    fun twitchReconnectRequestedSkipsBackoff() =
        runTest {
            val rig = Rig(this).start()
            val first = rig.transport.last
            first.send(privmsg(1))
            runCurrent()
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)

            first.send(RECONNECT)
            runCurrent()

            assertEquals(listOf(0L, 0L), rig.transport.connections.map { it.openedAt }, "no backoff")
            assertTrue(first.closed, "the old connection is no longer collected")
            assertEquals(ConnectionState.RECONNECTING, rig.state.connection)
            assertEquals(listOf("m-1"), rig.ids, "pending items survive and no system line is added")

            rig.transport.last.send(privmsg(2))
            runCurrent()
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
            rig.flush()
            assertEquals(listOf("m-1", "m-2"), rig.ids)
        }

    @Test
    fun historyReconnectRequestedIgnored() =
        runTest {
            val history = FakeHistory({ recentMessages(privmsg(1), RECONNECT, privmsg(2)) })
            val rig = Rig(this, history = history).start()
            rig.transport.last.send(privmsg(3))
            rig.flush()

            assertEquals(1, rig.transport.connections.size)
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
            assertTrue(rig.emissions.none { it.connection == ConnectionState.RECONNECTING })
            assertEquals(listOf("m-1", "m-2", "m-3"), rig.ids)
        }

    @Test
    fun crlfMultiLineFrame() =
        runTest {
            val rig = Rig(this).start()
            rig.transport.last.send(
                ircLine(5) + "\r\n" + ircLine(8) + "\r\n" + ircLine(38) + "\r\n" + ircLine(10) + "\r\n",
            )
            rig.flush()

            assertEquals(listOf(tid("a3eb"), tid("a44f"), tid("aa21")), rig.ids)
        }

    @Test
    fun lateEmoteSetRetokenizes() =
        runTest {
            val rig = Rig(this).start()
            rig.transport.last.send(privmsg(1, "KEKW"))
            rig.flush()
            val before = rig.state.items.single()
            assertEquals(listOf<MessagePart>(MessagePart.Text("KEKW")), before.parts)
            assertEquals(0, before.emoteGeneration)
            val emissions = rig.emissions.size

            rig.registry.put(ProviderSet(EmoteProvider.SEVENTV, EmoteScope.CHANNEL, listOf(KEKW)))
            runCurrent()

            val after = rig.state.items.single()
            assertEquals(listOf<MessagePart>(MessagePart.EmotePart(KEKW, source = "KEKW")), after.parts)
            assertEquals(rig.registry.snapshot.value.generation, after.emoteGeneration)
            assertEquals(emissions + 1, rig.emissions.size, "one emission for the change")
        }

    @Test
    fun bufferCappedAt1000() =
        runTest {
            val rig = Rig(this).start()
            rig.transport.last.send(*Array(1005) { privmsg(it) })
            rig.flush()
            assertEquals(1000, rig.state.items.size)
            assertEquals("m-5", rig.ids.first())
            assertEquals("m-1004", rig.ids.last())

            // An evicted id is forgotten, so it is accepted again; a buffered one is still a duplicate.
            rig.transport.last.send(privmsg(0), privmsg(500))
            rig.flush()
            assertEquals(1000, rig.state.items.size)
            assertEquals("m-6", rig.ids.first())
            assertEquals("m-0", rig.ids.last())
        }

    @Test
    fun historyFailureDoesNotBlock() =
        runTest {
            val history = FakeHistory({ throw IllegalStateException("history is down") })
            val rig = Rig(this, history = history).start()
            assertEquals(1, history.calls)
            assertEquals(1, rig.transport.connections.size)

            rig.transport.last.send(privmsg(1))
            rig.flush()
            assertEquals(ConnectionState.CONNECTED, rig.state.connection)
            assertEquals(listOf("m-1"), rig.ids)
        }

    @Test
    fun kickHistoryLiveAndPin() =
        runTest {
            val history = FakeHistory({ fixture("kick/history.json") })
            val rig = Rig(this, platform = Platform.KICK, history = history).start()
            assertEquals(5, rig.state.items.size)
            assertTrue(rig.state.items.all { it.message.historical })

            // Lines 5 and 6 are chat messages, line 24 pins a message.
            rig.transport.last.send(kickFrame(5), kickFrame(6), kickFrame(24))
            rig.flush()
            assertEquals(7, rig.state.items.size)
            val pinned = assertNotNull(rig.state.pinned)
            assertEquals("pinned text", pinned.message.text)
            assertEquals(HighlightType.KICK_PINNED, pinned.highlight?.type)

            rig.transport.last.send(kickFrame(25))
            rig.flush()
            assertNull(rig.state.pinned)

            // Kick has no back-fill after a reconnect.
            rig.transport.last.drop()
            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(2, rig.transport.connections.size)
            assertEquals(1, history.calls)
        }

    @Test
    fun highlightChangeReclassifies() =
        runTest {
            val rig = Rig(this, selfLogin = "user_me").start()
            rig.transport.last.send(privmsg(1, "hey @user_me"))
            rig.flush()
            assertEquals(
                HighlightType.MENTION,
                rig.state.items
                    .single()
                    .highlight
                    ?.type,
            )
            val emissions = rig.emissions.size

            rig.prefs.value = ChatPreferences(highlights = emptySet())
            runCurrent()

            assertNull(
                rig.state.items
                    .single()
                    .highlight,
            )
            assertEquals(emissions + 1, rig.emissions.size)
        }
}

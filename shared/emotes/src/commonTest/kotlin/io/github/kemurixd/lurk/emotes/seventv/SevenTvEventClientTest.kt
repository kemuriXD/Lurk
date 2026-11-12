package io.github.kemurixd.lurk.emotes.seventv

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.emotes.EmoteApi
import io.github.kemurixd.lurk.emotes.EmoteRegistry
import io.github.kemurixd.lurk.emotes.fixture
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val CHANNEL_ID = "1000500"
private const val CHANNEL_SET = "01AA0000000000000000000011"
private const val OTHER_SET = "01AA0000000000000000000099"

@OptIn(ExperimentalCoroutinesApi::class)
class SevenTvEventClientTest {
    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private class FakeConnection : WebSocketConnection {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var closed = false

        override val incoming: Flow<String> = inbound.consumeAsFlow()

        override suspend fun send(text: String) {
            if (closed) throw LurkException(LurkError.Offline)
            sent += text
        }

        override suspend fun close() {
            closed = true
            inbound.close()
        }

        fun push(text: String) {
            inbound.trySend(text)
        }

        fun pushFixture(name: String) = push(fixture("seventv/eventapi/$name"))

        fun drop() {
            inbound.close(LurkException(LurkError.Offline))
        }

        fun frames(): List<JsonObject> = sent.map { Json.parseToJsonElement(it).jsonObject }
    }

    private class FakeConnector(
        private val scheduler: TestCoroutineScheduler,
        private val failFirst: Int = 0,
        private val onConnect: (FakeConnection, Int) -> Unit = { _, _ -> },
    ) : WebSocketConnector {
        val connections = mutableListOf<FakeConnection>()
        val attemptTimes = mutableListOf<Long>()
        val urls = mutableListOf<String>()

        override suspend fun connect(
            url: String,
            headers: Map<String, String>,
        ): WebSocketConnection {
            urls += url
            attemptTimes += scheduler.currentTime
            if (attemptTimes.size <= failFirst) throw LurkException(LurkError.Offline)
            return FakeConnection().also {
                connections += it
                onConnect(it, connections.size - 1)
            }
        }
    }

    private val registry = EmoteRegistry()
    private val cosmetics = CosmeticsRegistry()
    private val channelCondition = mapOf("ctx" to "channel", "platform" to "TWITCH", "id" to CHANNEL_ID)

    private fun hello(
        session: String,
        interval: Duration = 25.seconds,
    ) = """{"op":1,"d":{"session_id":"$session","heartbeat_interval":${interval.inWholeMilliseconds}}}"""

    private val heartbeat = """{"op":2,"d":{"count":1}}"""
    private val endOfStream = """{"op":7,"d":{"code":4012,"message":"Reconnect"}}"""

    private fun subscribe(
        type: String,
        condition: Map<String, String>,
    ) = buildJsonObject {
        put("op", 35)
        putJsonObject("d") {
            put("type", type)
            putJsonObject("condition") { condition.forEach { (k, v) -> put(k, v) } }
        }
    }

    private fun emoteSetSub(setId: String) = subscribe("emote_set.*", mapOf("object_id" to setId))

    private val cosmeticSub get() = subscribe("cosmetic.*", channelCondition)
    private val entitlementSub get() = subscribe("entitlement.*", channelCondition)

    private fun resume(session: String) =
        buildJsonObject {
            put("op", 34)
            putJsonObject("d") { put("session_id", session) }
        }

    private fun TestScope.api(): EmoteApi {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        if (request.url.encodedPath.endsWith("/emote-sets/$CHANNEL_SET")) {
                            respond(fixture("seventv/emote-set.json"))
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                    }
                },
            )
        return EmoteApi(createHttpClient(engine))
    }

    private fun TestScope.start(
        connector: FakeConnector,
        setId: MutableStateFlow<String?>,
        backoff: Backoff = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1)),
    ): Job {
        val client =
            SevenTvEventClient(
                connector,
                api(),
                registry,
                cosmetics,
                TestDispatchers(StandardTestDispatcher(testScheduler)),
                backoff,
            )
        return backgroundScope.launch { client.run(Platform.TWITCH, CHANNEL_ID, setId) }.also { runCurrent() }
    }

    private fun emote(
        id: String,
        name: String,
    ) = Emote(id, name, EmoteProvider.SEVENTV, EmoteScope.CHANNEL, listOf(EmoteImage(1, "https://cdn.7tv.app/$id")))

    @Test
    fun helloSendsTheThreeSubscribes() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val conn = connector.connections.single()
            assertEquals(listOf("wss://events.7tv.io/v3"), connector.urls)
            assertTrue(conn.sent.isEmpty())
            conn.push(hello("s1"))
            runCurrent()
            assertEquals(3, conn.sent.size)
            assertEquals(setOf(emoteSetSub(CHANNEL_SET), cosmeticSub, entitlementSub), conn.frames().toSet())
        }

    @Test
    fun setIdKnownLaterIsSubscribed() =
        runTest {
            val connector = FakeConnector(testScheduler)
            val setId = MutableStateFlow<String?>(null)
            start(connector, setId)
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            runCurrent()
            assertEquals(setOf(cosmeticSub, entitlementSub), conn.frames().toSet())
            setId.value = CHANNEL_SET
            runCurrent()
            assertEquals(emoteSetSub(CHANNEL_SET), conn.frames().last())
            assertEquals(3, conn.sent.size)
        }

    @Test
    fun pushedEmoteIsFoundAndPulledIsNot() =
        runTest {
            registry.addEmote(
                EmoteProvider.SEVENTV,
                EmoteScope.CHANNEL,
                emote("01AA0000000000000000000E02", "oldEmote"),
            )
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            conn.pushFixture("dispatch-emote-set-update.json")
            runCurrent()
            val found = assertNotNull(registry.snapshot.value.find("pepeLaugh"))
            assertEquals(EmoteProvider.SEVENTV, found.provider)
            assertEquals(EmoteScope.CHANNEL, found.scope)
            assertNull(registry.snapshot.value.find("oldEmote"))
        }

    @Test
    fun updateForAnotherSetIsIgnored() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(OTHER_SET))
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            conn.pushFixture("dispatch-emote-set-update.json")
            runCurrent()
            assertNull(registry.snapshot.value.find("pepeLaugh"))
        }

    @Test
    fun personalSetEntitlementLoadsEmotes() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(null))
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            conn.pushFixture("dispatch-entitlement-emote-set.json")
            runCurrent()
            val personal = assertNotNull(registry.snapshot.value.find("Modding", "1000500"))
            assertEquals(EmoteScope.PERSONAL, personal.scope)
            assertNull(registry.snapshot.value.find("Modding"))
            assertTrue(emoteSetSub(CHANNEL_SET) in conn.frames())

            conn.pushFixture("dispatch-emote-set-update.json")
            runCurrent()
            val pushed = assertNotNull(registry.snapshot.value.find("pepeLaugh", "1000500"))
            assertEquals(EmoteScope.PERSONAL, pushed.scope)
            assertNull(registry.snapshot.value.find("pepeLaugh"))

            conn.push(
                fixture("seventv/eventapi/dispatch-entitlement-emote-set.json")
                    .replace("entitlement.create", "entitlement.delete"),
            )
            runCurrent()
            assertNull(registry.snapshot.value.find("Modding", "1000500"))
        }

    @Test
    fun cosmeticsReachTheRegistry() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            conn.pushFixture("dispatch-entitlement-paint.json")
            conn.pushFixture("dispatch-cosmetic-paint.json")
            runCurrent()
            assertEquals(
                "Northern Light",
                cosmetics.state.value
                    .paintFor("1000500")
                    ?.name,
            )
        }

    @Test
    fun heartbeatTimeoutReconnects() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val first = connector.connections.single()
            first.push(hello("s1", 25.seconds))
            runCurrent()
            val wait = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1)).delayFor(0)
            advanceTimeBy(75.seconds - 1.milliseconds)
            runCurrent()
            assertEquals(1, connector.connections.size)
            advanceTimeBy(1.milliseconds + wait)
            runCurrent()
            assertEquals(2, connector.connections.size)
            assertEquals((75.seconds + wait).inWholeMilliseconds, connector.attemptTimes[1])
            assertTrue(first.closed)
            val second = connector.connections[1]
            second.push(hello("s2", 25.seconds))
            runCurrent()
            assertEquals(resume("s1"), second.frames().first())
        }

    @Test
    fun anyMessageFeedsTheWatchdog() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val conn = connector.connections.single()
            conn.push(hello("s1", 25.seconds))
            runCurrent()
            advanceTimeBy(60.seconds)
            conn.pushFixture("dispatch-cosmetic-badge.json")
            runCurrent()
            advanceTimeBy(74.seconds)
            runCurrent()
            assertEquals(1, connector.connections.size)
            assertTrue(!conn.closed)
        }

    @Test
    fun endOfStreamBacksOff() =
        runTest {
            val connector =
                FakeConnector(testScheduler) { conn, index ->
                    conn.push(hello("s$index"))
                    conn.push(endOfStream)
                }
            start(connector, MutableStateFlow(CHANNEL_SET), Backoff(random = Random(1)))
            advanceTimeBy(10.minutes)
            runCurrent()
            val times = connector.attemptTimes
            assertTrue(times.size in 2 until 40, "connects: ${times.size}")
            val twin = Backoff(random = Random(1))
            times.zipWithNext().forEachIndexed { attempt, (before, after) ->
                val floor = twin.delayFor(attempt).inWholeMilliseconds
                assertTrue(after - before >= floor, "gap ${after - before} ms < $floor ms at attempt $attempt")
            }
            assertTrue(connector.connections.dropLast(1).all { it.closed })
        }

    @Test
    fun failedConnectBacksOff() =
        runTest {
            val connector = FakeConnector(testScheduler, failFirst = 2)
            start(connector, MutableStateFlow(CHANNEL_SET))
            advanceTimeBy(1.minutes)
            runCurrent()
            val twin = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1))
            val first = twin.delayFor(0).inWholeMilliseconds
            val second = twin.delayFor(1).inWholeMilliseconds
            assertEquals(listOf(0L, first, first + second), connector.attemptTimes)
            assertEquals(1, connector.connections.size)
        }

    @Test
    fun heartbeatResetsBackoff() =
        runTest {
            val connector =
                FakeConnector(testScheduler) { conn, index ->
                    conn.push(hello("s$index"))
                    if (index == 2) conn.push(heartbeat)
                    if (index < 3) conn.push(endOfStream)
                }
            start(connector, MutableStateFlow(CHANNEL_SET))
            advanceTimeBy(30.seconds)
            runCurrent()
            val twin = Backoff(base = 1.seconds, max = 30.seconds, random = Random(1))
            val expected = listOf(twin.delayFor(0), twin.delayFor(1), twin.delayFor(0))
            val gaps = connector.attemptTimes.zipWithNext { a, b -> (b - a).milliseconds }
            assertEquals(expected, gaps)
        }

    @Test
    fun resumeFailureResubscribes() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            val first = connector.connections.single()
            first.push(hello("s1"))
            runCurrent()
            first.pushFixture("reconnect.json")
            advanceTimeBy(30.seconds)
            runCurrent()
            val second = connector.connections[1]
            second.push(hello("s2"))
            runCurrent()
            assertEquals(listOf(resume("s1")), second.frames())
            second.pushFixture("ack-resume-failed.json")
            runCurrent()
            assertEquals(resume("s1"), second.frames().first())
            assertEquals(
                setOf(emoteSetSub(CHANNEL_SET), cosmeticSub, entitlementSub),
                second.frames().drop(1).toSet(),
            )
            assertEquals(4, second.sent.size)
        }

    @Test
    fun resumeSuccessSendsOnlyNewSubscriptions() =
        runTest {
            val connector = FakeConnector(testScheduler)
            val setId = MutableStateFlow<String?>(null)
            start(connector, setId)
            val first = connector.connections.single()
            first.push(hello("s1"))
            runCurrent()
            first.drop()
            runCurrent()
            setId.value = CHANNEL_SET
            advanceTimeBy(30.seconds)
            runCurrent()
            val second = connector.connections[1]
            second.push(hello("s2"))
            second.pushFixture("ack-resume.json")
            runCurrent()
            assertEquals(listOf(resume("s1"), emoteSetSub(CHANNEL_SET)), second.frames())
        }

    @Test
    fun resumingASessionThatNeverSubscribedResubscribes() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            connector.connections[0].push(hello("s1"))
            runCurrent()
            connector.connections[0].drop()
            advanceTimeBy(30.seconds)
            runCurrent()
            // The second session never gets its resume answered, so it holds no subscriptions.
            connector.connections[1].push(hello("s2"))
            runCurrent()
            connector.connections[1].drop()
            advanceTimeBy(30.seconds)
            runCurrent()
            val third = connector.connections[2]
            third.push(hello("s3"))
            third.pushFixture("ack-resume.json")
            runCurrent()
            assertEquals(resume("s2"), third.frames().first())
            assertEquals(
                setOf(emoteSetSub(CHANNEL_SET), cosmeticSub, entitlementSub),
                third.frames().drop(1).toSet(),
            )
        }

    @Test
    fun unansweredResumeResubscribesOnHeartbeat() =
        runTest {
            val connector = FakeConnector(testScheduler)
            start(connector, MutableStateFlow(CHANNEL_SET))
            connector.connections[0].push(hello("s1"))
            runCurrent()
            connector.connections[0].drop()
            advanceTimeBy(30.seconds)
            runCurrent()
            val second = connector.connections[1]
            second.push(hello("s2"))
            runCurrent()
            assertEquals(listOf(resume("s1")), second.frames())
            second.push(heartbeat)
            runCurrent()
            assertEquals(
                setOf(emoteSetSub(CHANNEL_SET), cosmeticSub, entitlementSub),
                second.frames().drop(1).toSet(),
            )
            assertEquals(4, second.sent.size)
        }

    @Test
    fun cancelClosesConnection() =
        runTest {
            val connector = FakeConnector(testScheduler)
            val job = start(connector, MutableStateFlow(CHANNEL_SET))
            val conn = connector.connections.single()
            conn.push(hello("s1"))
            runCurrent()
            job.cancel()
            runCurrent()
            assertTrue(conn.closed)
            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals(1, connector.attemptTimes.size)
        }
}

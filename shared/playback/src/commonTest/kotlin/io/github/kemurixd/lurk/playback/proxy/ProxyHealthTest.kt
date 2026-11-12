package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.playback.ProxyEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ProxyHealthTest {
    private val time = TestTimeSource()
    private val health = ProxyHealth(time)
    private val a = ProxyEndpoint.Http("127.0.0.1", 8080)
    private val b = ProxyEndpoint.Socks5("127.0.0.1", 1080)

    @Test
    fun unknownEndpointIsHealthyWithEmptyStats() {
        assertTrue(health.isHealthy(a))
        val stats = health.stats(a)
        assertNull(stats.lastLatency)
        assertNull(stats.lastFailure)
        assertEquals(0, stats.consecutiveFailures)
    }

    @Test
    fun failureMakesEndpointUnhealthyUntilCooldownPasses() {
        health.recordFailure(a)
        assertFalse(health.isHealthy(a))
        assertTrue(health.isHealthy(b))
        time += 1.minutes + 59.seconds
        assertFalse(health.isHealthy(a))
        time += 1.seconds
        assertTrue(health.isHealthy(a))
    }

    @Test
    fun failuresAccumulateAndSuccessResets() {
        health.recordFailure(a)
        health.recordFailure(a)
        assertEquals(2, health.stats(a).consecutiveFailures)
        health.recordSuccess(a, 80.milliseconds)
        val stats = health.stats(a)
        assertEquals(0, stats.consecutiveFailures)
        assertEquals(80.milliseconds, stats.lastLatency)
        assertFalse(health.isHealthy(a), "cooldown runs from the last failure")
    }

    @Test
    fun customCooldownApplies() {
        val short = ProxyHealth(time, cooldown = 10.seconds)
        short.recordFailure(a)
        time += 10.seconds
        assertTrue(short.isHealthy(a))
    }

    @Test
    fun concurrentRecordsAreSafe() =
        runTest {
            withContext(Dispatchers.Default) {
                (1..100)
                    .map { i ->
                        async {
                            if (i % 2 == 0) health.recordFailure(a) else health.recordSuccess(b, i.milliseconds)
                        }
                    }.awaitAll()
            }
            assertEquals(50, health.stats(a).consecutiveFailures)
            assertNotNull(health.stats(a).lastFailure)
            assertEquals(0, health.stats(b).consecutiveFailures)
            assertNotNull(health.stats(b).lastLatency)
            assertFalse(health.isHealthy(a))
            assertTrue(health.isHealthy(b))
        }

    @Test
    fun concurrentFailuresCountExactly() =
        runTest {
            withContext(Dispatchers.Default) {
                (1..100).map { async { health.recordFailure(a) } }.awaitAll()
            }
            assertEquals(100, health.stats(a).consecutiveFailures)
        }
}

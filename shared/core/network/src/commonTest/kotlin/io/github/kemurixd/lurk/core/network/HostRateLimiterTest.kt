package io.github.kemurixd.lurk.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HostRateLimiterTest {
    @Test
    fun sameHostCallsAreSpacedByMinInterval() =
        runTest {
            val limiter = HostRateLimiter(timeSource = testTimeSource)
            val start = testTimeSource.markNow()
            limiter.acquire("a")
            limiter.acquire("a")
            assertTrue(start.elapsedNow() >= 1.seconds, "elapsed ${start.elapsedNow()}")
        }

    @Test
    fun otherHostsDoNotWait() =
        runTest {
            val limiter = HostRateLimiter(timeSource = testTimeSource)
            val start = testTimeSource.markNow()
            limiter.acquire("a")
            limiter.acquire("b")
            assertEquals(Duration.ZERO, start.elapsedNow())
        }

    @Test
    fun blockUntilDelaysTheNextAcquire() =
        runTest {
            val limiter = HostRateLimiter(timeSource = testTimeSource)
            limiter.blockUntil("a", 30.seconds)
            val start = testTimeSource.markNow()
            limiter.acquire("a")
            assertEquals(30.seconds, start.elapsedNow())
        }
}

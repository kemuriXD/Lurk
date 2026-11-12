package io.github.kemurixd.lurk.core.network

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackoffTest {
    @Test
    fun delaysStayWithinTheCap() {
        val backoff = Backoff(random = Random(1))
        for (attempt in 0..10) {
            val delay = backoff.delayFor(attempt)
            assertTrue(delay in Duration.ZERO..60.seconds, "attempt $attempt gave $delay")
        }
    }

    @Test
    fun delaysStayUnderTheExponentialCeiling() {
        val backoff = Backoff(random = Random(1))
        repeat(50) {
            assertTrue(backoff.delayFor(0) <= 500.milliseconds)
            assertTrue(backoff.delayFor(2) <= 2.seconds)
        }
        assertTrue(backoff.delayFor(Int.MAX_VALUE) <= 60.seconds)
    }

    @Test
    fun retryAfterIsAFloor() {
        val backoff = Backoff(random = Random(1))
        assertTrue(backoff.delayFor(0, retryAfter = 7.seconds) >= 7.seconds)
    }
}

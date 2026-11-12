package io.github.kemurixd.lurk.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RetryAfterTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun retryAfterDeltaSeconds() {
        assertEquals(5.seconds, parseRetryAfter("5", now))
        assertEquals(5.seconds, parseRetryAfter(" 5 ", now))
    }

    @Test
    fun retryAfterHttpDate() {
        assertEquals(30.seconds, parseRetryAfter("Wed, 11 Nov 2026 12:00:30 GMT", now))
    }

    @Test
    fun retryAfterClamped() {
        assertEquals(0.seconds, parseRetryAfter("-3", now))
        assertEquals(10.minutes, parseRetryAfter("99999", now))
        assertEquals(0.seconds, parseRetryAfter("Wed, 11 Nov 2026 11:00:00 GMT", now))
        assertNull(parseRetryAfter("soon", now))
        assertNull(parseRetryAfter(null, now))
    }
}

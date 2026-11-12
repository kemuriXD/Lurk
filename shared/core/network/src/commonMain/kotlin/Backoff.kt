package io.github.kemurixd.lurk.core.network

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Exponential backoff with full jitter. */
class Backoff(
    private val base: Duration = 500.milliseconds,
    private val max: Duration = 60.seconds,
    private val random: Random = Random.Default,
) {
    /**
     * A random delay in `0..min(max, base * 2^attempt)`, raised to [retryAfter] when the server
     * asked for longer.
     */
    fun delayFor(
        attempt: Int,
        retryAfter: Duration? = null,
    ): Duration {
        val ceiling = minOf(max, base * (1L shl attempt.coerceIn(0, MAX_SHIFT)).toDouble())
        val jitter = random.nextLong(0, ceiling.inWholeMilliseconds + 1).milliseconds
        return maxOf(jitter, retryAfter ?: Duration.ZERO)
    }

    private companion object {
        // 2^30 times any sane base is already far past any sane max.
        const val MAX_SHIFT = 30
    }
}

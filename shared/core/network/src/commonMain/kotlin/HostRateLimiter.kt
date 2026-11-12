package io.github.kemurixd.lurk.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Spaces requests to the same host by at least [minInterval]; different hosts never wait on each other. */
class HostRateLimiter(
    private val minInterval: Duration = 1.seconds,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    private val mutex = Mutex()

    // Guarded by mutex: the earliest moment each host's next request may start.
    private val nextSlot = mutableMapOf<String, ComparableTimeMark>()

    // Written by the non-suspending blockUntil, so it uses an atomic update instead of the mutex.
    private val blocked = MutableStateFlow<Map<String, ComparableTimeMark>>(emptyMap())

    /** Suspends until [minInterval] has passed since [host]'s previous acquire, and past any block. */
    suspend fun acquire(host: String) {
        val wait =
            mutex.withLock {
                val now = timeSource.markNow()
                val start = listOfNotNull(now, nextSlot[host], blocked.value[host]).max()
                nextSlot[host] = start + minInterval
                start - now
            }
        if (wait.isPositive()) delay(wait)
    }

    /** Holds every acquire for [host] for [duration] from now, e.g. after a 429. */
    fun blockUntil(
        host: String,
        duration: Duration,
    ) {
        val until = timeSource.markNow() + duration
        blocked.update { current ->
            val existing = current[host]
            if (existing != null && existing >= until) current else current + (host to until)
        }
    }
}

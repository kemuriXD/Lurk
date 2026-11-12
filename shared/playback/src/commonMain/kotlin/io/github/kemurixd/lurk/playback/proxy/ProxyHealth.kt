package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.playback.ProxyEndpoint
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** What is known about one proxy. [lastFailure] is null until it first fails. */
data class ProxyStats(
    val lastLatency: Duration?,
    val lastFailure: ComparableTimeMark?,
    val consecutiveFailures: Int,
)

/**
 * Tracks how each proxy has behaved, keyed by endpoint equality. One instance is shared for the whole app
 * process. State lives in an immutable map swapped with compare-and-set, so recording is thread-safe and
 * never suspends.
 */
@OptIn(ExperimentalAtomicApi::class)
class ProxyHealth(
    private val timeSource: TimeSource.WithComparableMarks,
    private val cooldown: Duration = 2.minutes,
) {
    private val state = AtomicReference<Map<ProxyEndpoint, ProxyStats>>(emptyMap())

    /** Clears the failure streak and keeps the measured [latency]. */
    fun recordSuccess(
        endpoint: ProxyEndpoint,
        latency: Duration,
    ) = update(endpoint) { it.copy(lastLatency = latency, consecutiveFailures = 0) }

    fun recordFailure(endpoint: ProxyEndpoint) {
        val now = timeSource.markNow()
        update(endpoint) { it.copy(lastFailure = now, consecutiveFailures = it.consecutiveFailures + 1) }
    }

    /** False while the last failure is younger than the cooldown. A success does not clear it early. */
    fun isHealthy(endpoint: ProxyEndpoint): Boolean {
        val failedAt = stats(endpoint).lastFailure ?: return true
        return failedAt.elapsedNow() >= cooldown
    }

    fun stats(endpoint: ProxyEndpoint): ProxyStats = state.load()[endpoint] ?: EMPTY

    private inline fun update(
        endpoint: ProxyEndpoint,
        change: (ProxyStats) -> ProxyStats,
    ) {
        while (true) {
            val current = state.load()
            val next = current + (endpoint to change(current[endpoint] ?: EMPTY))
            if (state.compareAndSet(current, next)) return
        }
    }

    private companion object {
        val EMPTY = ProxyStats(lastLatency = null, lastFailure = null, consecutiveFailures = 0)
    }
}

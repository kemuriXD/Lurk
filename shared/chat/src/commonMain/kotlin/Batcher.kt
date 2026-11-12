package io.github.kemurixd.lurk.chat

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Groups items into lists. The first item of a batch starts a timer, and the batch is emitted
 * [interval] later with everything collected so far. Batches are never empty. When the upstream
 * completes, pending items are emitted at once; when it fails, they are emitted first and the
 * error is rethrown afterwards. Upstream items are buffered without limit, so a slow collector
 * never causes a loss. Time comes from `delay`, so virtual time works in tests.
 */
fun <T> Flow<T>.batched(interval: Duration = 1.seconds): Flow<List<T>> {
    require(interval.isPositive()) { "interval must be positive" }
    val upstream = this
    return flow {
        coroutineScope {
            val channel = Channel<T>(Channel.UNLIMITED)
            // Upstream failures travel through the channel, so the pending items can be flushed
            // before the error surfaces. The first close wins; the later plain close is a no-op.
            val producer =
                launch {
                    try {
                        upstream.catch { channel.close(it) }.collect { channel.send(it) }
                    } finally {
                        channel.close()
                    }
                }
            try {
                var failure: Throwable? = null
                var finished = false
                while (!finished) {
                    val batch = ArrayList<T>()
                    val first = channel.receiveCatching()
                    if (first.isClosed) {
                        failure = first.exceptionOrNull()
                        finished = true
                    } else {
                        batch += first.getOrThrow()
                        // A select that takes the deadline clause cannot consume an element, so
                        // nothing is lost at the flush instant (unlike a cancelled receive).
                        val deadline = launch { delay(interval) }
                        var waiting = true
                        while (waiting) {
                            select {
                                deadline.onJoin { waiting = false }
                                channel.onReceiveCatching { next ->
                                    if (next.isClosed) {
                                        failure = next.exceptionOrNull()
                                        finished = true
                                        waiting = false
                                    } else {
                                        batch += next.getOrThrow()
                                    }
                                }
                            }
                        }
                        deadline.cancel()
                    }
                    if (batch.isNotEmpty()) emit(batch)
                    failure?.let { throw it }
                }
            } finally {
                producer.cancel()
                channel.cancel()
            }
        }
    }
}

package io.github.kemurixd.lurk.chat

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BatcherTest {
    @Test
    fun itemsWithinOneIntervalFormOneBatchAtTheDeadline() =
        runTest {
            val source = Channel<Int>(Channel.UNLIMITED)
            val seen = mutableListOf<Pair<Long, List<Int>>>()
            val job =
                launch {
                    source.receiveAsFlow().batched().collect { seen += currentTime to it }
                }
            source.send(1)
            delay(300)
            source.send(2)
            delay(600)
            source.send(3)
            delay(200)
            assertEquals(listOf(1000L to listOf(1, 2, 3)), seen)
            source.close()
            job.join()
        }

    @Test
    fun laterItemStartsANewTimer() =
        runTest {
            val source = Channel<Int>(Channel.UNLIMITED)
            val seen = mutableListOf<Pair<Long, List<Int>>>()
            val job =
                launch {
                    source.receiveAsFlow().batched().collect { seen += currentTime to it }
                }
            source.send(1)
            delay(1500)
            source.send(2)
            delay(1100)
            assertEquals(listOf(1000L to listOf(1), 2500L to listOf(2)), seen)
            source.close()
            job.join()
        }

    @Test
    fun idleUpstreamEmitsNothing() =
        runTest {
            val source = Channel<Int>(Channel.UNLIMITED)
            source.receiveAsFlow().batched().test {
                expectNoEvents()
                delay(10_000)
                expectNoEvents()
                source.close()
                awaitComplete()
            }
        }

    @Test
    fun completionFlushesPendingItemsAtOnce() =
        runTest {
            flow {
                emit(1)
                emit(2)
            }.batched().test {
                assertEquals(listOf(1, 2), awaitItem())
                awaitComplete()
            }
            assertEquals(0L, currentTime)
        }

    @Test
    fun emptyUpstreamCompletesWithoutBatches() =
        runTest {
            flow<Int> { }.batched().test { awaitComplete() }
        }

    @Test
    fun fiveThousandItemsOverThreeSecondsKeepOrderAndCount() =
        runTest {
            val batches =
                flow {
                    repeat(5_000) {
                        emit(it)
                        if (it % 5 == 4) delay(3)
                    }
                }.batched().toList()
            assertTrue(batches.size <= 4, "got ${batches.size} batches")
            assertTrue(batches.none { it.isEmpty() })
            assertEquals((0 until 5_000).toList(), batches.flatten())
        }

    @Test
    fun slowCollectorLosesNothing() =
        runTest {
            val received = mutableListOf<Int>()
            flow {
                repeat(30) {
                    emit(it)
                    delay(200)
                }
            }.batched().collect {
                received += it
                delay(3_000)
            }
            assertEquals((0 until 30).toList(), received)
        }

    @Test
    fun upstreamErrorFlushesPendingItemsThenPropagates() =
        runTest {
            flow {
                emit(1)
                emit(2)
                throw IllegalStateException("boom")
            }.batched().test {
                assertEquals(listOf(1, 2), awaitItem())
                assertEquals("boom", awaitError().message)
            }
        }

    @Test
    fun upstreamErrorWithNothingPendingPropagates() =
        runTest {
            assertFailsWith<IllegalStateException> {
                flow<Int> { throw IllegalStateException("boom") }.batched().toList()
            }
        }

    @Test
    fun cancellingTheCollectorCancelsTheUpstream() =
        runTest {
            val cancelled = CompletableDeferred<Unit>()
            val upstream: Flow<Int> =
                flow {
                    emit(1)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            upstream.batched().test {
                assertEquals(listOf(1), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelled.await()
        }

    @Test
    fun customIntervalIsHonoured() =
        runTest {
            val source = Channel<Int>(Channel.UNLIMITED)
            val seen = mutableListOf<Pair<Long, List<Int>>>()
            val job =
                launch {
                    source.receiveAsFlow().batched(2.seconds).collect { seen += currentTime to it }
                }
            source.send(1)
            delay(2_500)
            assertEquals(listOf(2000L to listOf(1)), seen)
            source.close()
            job.join()
        }
}

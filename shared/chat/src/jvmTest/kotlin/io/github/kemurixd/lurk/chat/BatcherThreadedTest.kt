package io.github.kemurixd.lurk.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class BatcherThreadedTest {
    @Test
    fun noItemIsLostUnderRealThreadingWithAShortInterval() =
        runBlocking {
            val seen = mutableListOf<Int>()
            withTimeout(60_000) {
                flow {
                    repeat(3_000) {
                        emit(it)
                        // Spacing equals the interval, so arrivals keep landing on the flush instant.
                        if (it % 3 == 0) delay(5)
                    }
                }.flowOn(Dispatchers.Default)
                    .batched(5.milliseconds)
                    .flowOn(Dispatchers.Default)
                    .collect { seen += it }
            }
            assertEquals((0 until 3_000).toList(), seen)
        }
}

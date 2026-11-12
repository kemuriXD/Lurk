package io.github.kemurixd.lurk.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RingBufferTest {
    @Test
    fun addingBeyondCapacityEvictsExactlyTheOldest() {
        val buffer = RingBuffer<Int>(1000)
        assertEquals(emptyList(), buffer.addAll((1..1000).toList()))
        assertEquals(listOf(1), buffer.addAll(listOf(1001)))
        assertEquals(1000, buffer.size)
        assertEquals((2..1001).toList(), buffer.toList())
    }

    @Test
    fun oversizedBatchEvictsOldestFirstInOrder() {
        val buffer = RingBuffer<Int>(3)
        buffer.addAll(listOf(1, 2))
        assertEquals(listOf(1, 2, 3, 4), buffer.addAll(listOf(3, 4, 5, 6, 7)))
        assertEquals(listOf(5, 6, 7), buffer.toList())
    }

    @Test
    fun replaceAllKeepsOrder() {
        val buffer = RingBuffer<Int>(5)
        buffer.addAll(listOf(1, 2, 3))
        buffer.replaceAll { if (it == 2) 20 else it }
        assertEquals(listOf(1, 20, 3), buffer.toList())
    }

    @Test
    fun removeIfKeepsOrder() {
        val buffer = RingBuffer<Int>(5)
        buffer.addAll(listOf(1, 2, 3, 4, 5))
        buffer.removeIf { it % 2 == 0 }
        assertEquals(listOf(1, 3, 5), buffer.toList())
        assertEquals(3, buffer.size)
        buffer.addAll(listOf(6, 7, 8))
        assertEquals(listOf(3, 5, 6, 7, 8), buffer.toList())
    }

    @Test
    fun zeroCapacityIsRejected() {
        assertFailsWith<IllegalArgumentException> { RingBuffer<Int>(0) }
    }
}

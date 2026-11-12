package io.github.kemurixd.lurk.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class LowLatencyPolicyTest {
    @Test
    fun lowLatencyOnAndCapableTunesAroundTheSegmentLength() {
        val t = LowLatencyPolicy.tuning(lowLatencyOn = true, capable = true, segment = 2.seconds)
        assertEquals(5.seconds, t.targetOffset)
        assertEquals(4.seconds, t.minOffset)
        assertEquals(11.seconds, t.maxOffset)
        assertEquals(0.97f, t.minSpeed)
        assertEquals(1.05f, t.maxSpeed)
    }

    @Test
    fun anyMissingConditionLeavesThePlayerDefaults() {
        val cases =
            listOf(
                LowLatencyPolicy.tuning(lowLatencyOn = false, capable = true, segment = 2.seconds),
                LowLatencyPolicy.tuning(lowLatencyOn = true, capable = false, segment = 2.seconds),
                LowLatencyPolicy.tuning(lowLatencyOn = true, capable = true, segment = null),
            )
        cases.forEach { t ->
            assertNull(t.targetOffset)
            assertNull(t.minOffset)
            assertNull(t.maxOffset)
            assertEquals(1f, t.minSpeed)
            assertEquals(1f, t.maxSpeed)
        }
    }
}

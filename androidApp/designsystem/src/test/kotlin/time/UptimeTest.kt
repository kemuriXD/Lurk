package io.github.kemurixd.lurk.designsystem.time

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UptimeTest {
    @Test
    fun uptimeIsHoursMinutesSeconds() {
        assertEquals("0:00:00", formatUptime(Duration.ZERO))
        assertEquals("0:05:03", formatUptime(5.minutes + 3.seconds))
        assertEquals("1:47:59", formatUptime(1.hours + 47.minutes + 59.seconds + 999.milliseconds))
        assertEquals("25:00:00", formatUptime(25.hours))
    }

    @Test
    fun aStartTimeAheadOfTheClockReadsZero() {
        assertEquals("0:00:00", formatUptime((-3).seconds))
    }
}

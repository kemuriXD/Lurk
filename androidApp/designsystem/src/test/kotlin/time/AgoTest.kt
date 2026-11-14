package io.github.kemurixd.lurk.designsystem.time

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AgoTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun ago(elapsed: Duration) = agoOf(then = now - elapsed, now = now)

    @Test
    fun underAMinuteIsJustNow() {
        assertEquals(Ago.JustNow, ago(Duration.ZERO))
        assertEquals(Ago.JustNow, ago(59.seconds))
    }

    @Test
    fun aTimeAfterNowIsJustNow() {
        assertEquals(Ago.JustNow, ago((-5).minutes))
    }

    @Test
    fun minutesUpToAnHour() {
        assertEquals(Ago.Minutes(1), ago(60.seconds))
        assertEquals(Ago.Minutes(59), ago(59.minutes + 59.seconds))
    }

    @Test
    fun hoursUpToADay() {
        assertEquals(Ago.Hours(1), ago(1.hours))
        assertEquals(Ago.Hours(23), ago(23.hours + 59.minutes))
    }

    @Test
    fun oneDayIsYesterday() {
        assertEquals(Ago.Yesterday, ago(1.days))
        assertEquals(Ago.Yesterday, ago(2.days - 1.seconds))
    }

    @Test
    fun daysFromTwoToSix() {
        assertEquals(Ago.Days(2), ago(2.days))
        assertEquals(Ago.Days(6), ago(7.days - 1.seconds))
    }

    @Test
    fun weeksFromSevenDaysToAMonth() {
        assertEquals(Ago.Weeks(1), ago(7.days))
        assertEquals(Ago.Weeks(1), ago(13.days))
        assertEquals(Ago.Weeks(2), ago(14.days))
        assertEquals(Ago.Weeks(4), ago(29.days))
    }

    @Test
    fun monthsAreThirtyDaysUpToEleven() {
        assertEquals(Ago.Months(1), ago(30.days))
        assertEquals(Ago.Months(1), ago(59.days))
        assertEquals(Ago.Months(2), ago(60.days))
        assertEquals(Ago.Months(11), ago(330.days))
        assertEquals(Ago.Months(11), ago(364.days))
    }

    @Test
    fun aYearOrMoreIsYearsNeverADate() {
        assertEquals(Ago.Years(1), ago(365.days))
        assertEquals(Ago.Years(1), ago(729.days))
        assertEquals(Ago.Years(2), ago(730.days))
        assertEquals(Ago.Years(15), ago((15 * 365 + 10).days))
    }
}

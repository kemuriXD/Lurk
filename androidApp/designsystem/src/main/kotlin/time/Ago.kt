package io.github.kemurixd.lurk.designsystem.time

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val MAX_MONTHS = 11

/** How long ago something was, rounded down to one unit. Never a calendar date, however long ago. */
sealed interface Ago {
    data object JustNow : Ago

    data class Minutes(
        val count: Int,
    ) : Ago

    data class Hours(
        val count: Int,
    ) : Ago

    data object Yesterday : Ago

    data class Days(
        val count: Int,
    ) : Ago

    data class Weeks(
        val count: Int,
    ) : Ago

    data class Months(
        val count: Int,
    ) : Ago

    data class Years(
        val count: Int,
    ) : Ago
}

/**
 * How long before [now] [then] was. Under a minute (or a time after [now]) is just now; one whole day is yesterday;
 * 2 to 6 days are days; 7 to 29 days are weeks; a month is 30 days, up to 11; from 365 days on it is years.
 */
fun agoOf(
    then: Instant,
    now: Instant,
): Ago {
    val elapsed = now - then
    val days = elapsed.inWholeDays.toInt()
    return when {
        elapsed < 1.minutes -> Ago.JustNow
        elapsed < 1.hours -> Ago.Minutes(elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> Ago.Hours(elapsed.inWholeHours.toInt())
        days == 1 -> Ago.Yesterday
        days < DAYS_PER_WEEK -> Ago.Days(days)
        days < DAYS_PER_MONTH -> Ago.Weeks(days / DAYS_PER_WEEK)
        days < DAYS_PER_YEAR -> Ago.Months((days / DAYS_PER_MONTH).coerceAtMost(MAX_MONTHS))
        else -> Ago.Years(days / DAYS_PER_YEAR)
    }
}

package io.github.kemurixd.lurk.featureui.channel

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long ago something was, rounded down to one unit. */
internal sealed interface Ago {
    data object JustNow : Ago

    data class Minutes(
        val count: Int,
    ) : Ago

    data class Hours(
        val count: Int,
    ) : Ago

    data class Days(
        val count: Int,
    ) : Ago
}

internal fun agoOf(
    then: Instant,
    now: Instant,
): Ago {
    val elapsed = now - then
    return when {
        elapsed < 1.minutes -> Ago.JustNow
        elapsed < 1.hours -> Ago.Minutes(elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> Ago.Hours(elapsed.inWholeHours.toInt())
        else -> Ago.Days(elapsed.inWholeDays.toInt())
    }
}

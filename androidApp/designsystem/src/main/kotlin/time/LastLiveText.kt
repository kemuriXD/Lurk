package io.github.kemurixd.lurk.designsystem.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kemurixd.lurk.designsystem.R
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Where "now" comes from for relative times. The app uses the system clock; tests provide their own. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.System }

/** The current time from [LocalClock], read again every [every] so "5 minutes ago" keeps up while on screen. */
@Composable
fun rememberNow(every: Duration = 1.minutes): Instant = rememberNowState(every).value

/**
 * The ticking time of [rememberNow], as a state. Whoever reads its value recomposes on each tick, so a screen hands it
 * down and only the text that shows a relative time reads it; the screen and its other rows stay put.
 */
@Composable
fun rememberNowState(every: Duration = 1.minutes): State<Instant> {
    val clock = LocalClock.current
    val now = remember(clock) { mutableStateOf(clock.now()) }
    LaunchedEffect(clock, every) {
        while (true) {
            delay(every)
            now.value = clock.now()
        }
    }
    return now
}

/** "Offline · last live 3 days ago", or just "Offline" when [lastLiveAt] is unknown (Figma, Android v1 04 Search). */
@Composable
fun offlineStatus(
    lastLiveAt: Instant?,
    now: Instant,
): String =
    if (lastLiveAt == null) {
        stringResource(R.string.offline)
    } else {
        stringResource(R.string.offline_last_live, agoText(agoOf(lastLiveAt, now)))
    }

/** [ago] as text: "just now", "yesterday", "3 days ago" and so on. */
@Composable
fun agoText(ago: Ago): String =
    when (ago) {
        Ago.JustNow -> stringResource(R.string.ago_just_now)
        is Ago.Minutes -> pluralStringResource(R.plurals.ago_minutes, ago.count, ago.count)
        is Ago.Hours -> pluralStringResource(R.plurals.ago_hours, ago.count, ago.count)
        Ago.Yesterday -> stringResource(R.string.ago_yesterday)
        is Ago.Days -> pluralStringResource(R.plurals.ago_days, ago.count, ago.count)
        is Ago.Weeks -> pluralStringResource(R.plurals.ago_weeks, ago.count, ago.count)
        is Ago.Months -> pluralStringResource(R.plurals.ago_months, ago.count, ago.count)
        is Ago.Years -> pluralStringResource(R.plurals.ago_years, ago.count, ago.count)
    }

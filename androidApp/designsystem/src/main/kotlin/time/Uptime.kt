package io.github.kemurixd.lurk.designsystem.time

import android.content.res.Resources
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kemurixd.lurk.designsystem.R
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Instant

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_SECOND = 1_000L

/** How long a stream has run, as H:MM:SS ("1:47:59"). Hours keep counting past a day; a negative time reads zero. */
fun formatUptime(elapsed: Duration): String {
    val seconds = elapsed.inWholeSeconds.coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return "$hours:${minutes.twoDigits()}:${rest.twoDigits()}"
}

/**
 * How long a stream has run, as a screen reader should say it: "1 hour 49 minutes", never "1:49:00". Seconds are left
 * out, and under a minute it reads "live for less than a minute".
 */
fun spokenUptime(
    elapsed: Duration,
    resources: Resources,
): String {
    val minutesInTotal = (elapsed.inWholeSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
    if (minutesInTotal == 0) return resources.getString(R.string.cd_live_for_under_minute)
    val hours = minutesInTotal / MINUTES_PER_HOUR
    val minutes = minutesInTotal % MINUTES_PER_HOUR
    val parts =
        listOfNotNull(
            hours.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.cd_uptime_hours, it, it) },
            minutes.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.cd_uptime_minutes, it, it) },
        )
    return resources.getString(R.string.cd_live_for, parts.joinToString(" "))
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')

/**
 * The time since [startedAt], ticking each second on the second, but only while the app is at least started: in the
 * background it stands still, and on return it shows the right time at once before ticking again.
 * The clock is read here and nowhere else, so a tick recomposes this text alone, never the card around it. The ticking
 * lives as long as this text is composed: a card scrolled off screen leaves the lazy list and stops.
 */
@Composable
fun UptimeText(
    startedAt: Instant,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val clock = LocalClock.current
    var elapsed by remember(clock, startedAt) { mutableStateOf(clock.now() - startedAt) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, startedAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            elapsed = clock.now() - startedAt
            while (true) {
                val intoSecond = (clock.now() - startedAt).inWholeMilliseconds.mod(MILLIS_PER_SECOND)
                delay(MILLIS_PER_SECOND - intoSecond)
                elapsed = clock.now() - startedAt
            }
        }
    }
    Text(text = formatUptime(elapsed), modifier = modifier, style = style, color = color, maxLines = 1)
}

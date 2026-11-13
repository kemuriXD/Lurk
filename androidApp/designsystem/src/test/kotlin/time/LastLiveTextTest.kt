package io.github.kemurixd.lurk.designsystem.time

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LastLiveTextTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun show(elapsed: Duration?) {
        rule.setContent { Text(offlineStatus(lastLiveAt = elapsed?.let { now - it }, now = now)) }
    }

    @Test
    fun unknownTimeIsJustOffline() {
        show(null)
        rule.onNodeWithText("Offline").assertExists()
    }

    @Test
    fun justNow() {
        show(10.seconds)
        rule.onNodeWithText("Offline · last live just now").assertExists()
    }

    @Test
    fun oneMinuteIsSingular() {
        show(1.minutes)
        rule.onNodeWithText("Offline · last live 1 minute ago").assertExists()
    }

    @Test
    fun hoursArePlural() {
        show(5.hours)
        rule.onNodeWithText("Offline · last live 5 hours ago").assertExists()
    }

    @Test
    fun oneDayIsYesterday() {
        show(30.hours)
        rule.onNodeWithText("Offline · last live yesterday").assertExists()
    }

    @Test
    fun days() {
        show(3.days)
        rule.onNodeWithText("Offline · last live 3 days ago").assertExists()
    }

    @Test
    fun oneWeek() {
        show(8.days)
        rule.onNodeWithText("Offline · last live 1 week ago").assertExists()
    }

    @Test
    fun months() {
        show(65.days)
        rule.onNodeWithText("Offline · last live 2 months ago").assertExists()
    }

    @Test
    fun yearsNotADate() {
        show(800.days)
        rule.onNodeWithText("Offline · last live 2 years ago").assertExists()
    }

    @Test
    fun nowComesFromTheProvidedClockAndTicks() {
        val clock = MutableClock(now)
        val then = now - 59.seconds
        rule.setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                Text(offlineStatus(lastLiveAt = then, now = rememberNow()))
            }
        }
        rule.onNodeWithText("Offline · last live just now").assertExists()

        clock.time = now + 1.minutes
        rule.mainClock.advanceTimeBy(1.minutes.inWholeMilliseconds)
        rule.onNodeWithText("Offline · last live 1 minute ago").assertExists()
    }
}

private class MutableClock(
    var time: Instant,
) : Clock {
    override fun now(): Instant = time
}

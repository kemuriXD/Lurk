package io.github.kemurixd.lurk.designsystem.time

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UptimeTextTest {
    @get:Rule val rule = createComposeRule()

    private var now = Instant.parse("2026-11-13T12:00:00Z")
    private val startedAt = now - 5.minutes
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }
    private val owner =
        object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }

    private fun moveTo(state: Lifecycle.State) = rule.runOnUiThread { owner.registry.currentState = state }

    private fun show() =
        rule.setContent {
            CompositionLocalProvider(LocalClock provides clock, LocalLifecycleOwner provides owner) {
                UptimeText(startedAt, Modifier.testTag("uptime"))
            }
        }

    private fun assertShows(text: String) = rule.onNodeWithTag("uptime").assertTextEquals(text)

    @Test
    fun itTicksWhileStarted() {
        moveTo(Lifecycle.State.RESUMED)
        show()
        now += 1.seconds
        rule.mainClock.advanceTimeBy(1_100)
        assertShows("0:05:01")
    }

    @Test
    fun itDoesNotTickWhileOnlyCreated() {
        moveTo(Lifecycle.State.CREATED)
        show()
        assertShows("0:05:00")
        now += 10.seconds
        rule.mainClock.advanceTimeBy(5_000)
        assertShows("0:05:00")
    }

    @Test
    fun itStopsWhenTheAppGoesToTheBackground() {
        moveTo(Lifecycle.State.RESUMED)
        show()
        moveTo(Lifecycle.State.CREATED)
        rule.waitForIdle()
        now += 10.seconds
        rule.mainClock.advanceTimeBy(5_000)
        assertShows("0:05:00")
    }

    @Test
    fun onReturnItShowsTheRightValueAtOnceAndTicksAgain() {
        moveTo(Lifecycle.State.CREATED)
        show()
        now += 30.seconds
        rule.mainClock.advanceTimeBy(5_000)
        moveTo(Lifecycle.State.STARTED)
        rule.waitForIdle()
        assertShows("0:05:30")
        now += 1.seconds
        rule.mainClock.advanceTimeBy(1_100)
        assertShows("0:05:31")
    }
}

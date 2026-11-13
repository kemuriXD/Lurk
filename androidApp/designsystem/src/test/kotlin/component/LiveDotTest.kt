package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveDotTest {
    @get:Rule val rule = createComposeRule()

    @After
    fun restoreAnimations() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun liveChannel() =
        Channel(
            ChannelRef(Platform.TWITCH, "streamer1"),
            "1",
            "streamer1",
            "Streamer One",
            null,
            null,
            null,
            null,
            testStream(),
            null,
        )

    @Test
    fun theDotIsDescribedAsLive() {
        rule.setContent { LurkTheme(dynamicColor = false) { LiveDot() } }
        rule.onNodeWithContentDescription("Live").assertExists()
    }

    @Test
    fun thereIsNoLivePillAnywhere() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                Box {
                    StreamThumbnail(testStream(), Modifier.size(160.dp, 90.dp))
                    ChannelRow(liveChannel(), onClick = {})
                }
            }
        }
        rule.onAllNodesWithText("LIVE", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theThumbnailOverlayIsOneNodeReadingLiveAndViewers() {
        rule.setContent {
            LurkTheme(
                dynamicColor = false,
            ) { StreamThumbnail(testStream(), Modifier.size(160.dp, 90.dp)) }
        }
        rule.onNodeWithContentDescription("Live, 12.4K viewers").assertExists()
        rule.onAllNodesWithText("12.4K viewers").assertCountEquals(0)
    }

    @Test
    fun theDotStandsStillWhenAnimationsAreOff() {
        setAnimatorScale(0f)
        var pulses: Boolean? = null
        rule.setContent { pulses = rememberLivePulses() }
        assertEquals(false, pulses)
    }

    @Test
    fun theDotPulsesWhenAnimationsAreOn() {
        setAnimatorScale(1f)
        var pulses: Boolean? = null
        rule.setContent { pulses = rememberLivePulses() }
        assertTrue(pulses == true)
    }

    @Test
    fun aPulsingDotRecomposesNeitherItselfNorItsParent() {
        setAnimatorScale(1f)
        var reads = 0
        val showing = mutableStateOf(true)
        // Compose's test clock refuses infinite animations while it advances by itself; stepping it by hand lets this one run.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CountingStrings(onRead = { id -> if (id == R.string.live || id == R.string.viewers_count) reads++ }) {
                LurkTheme(dynamicColor = false) { Parent(showing.value) }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        reads = 0
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals("recompositions while the dot pulsed", 0, reads)
    }

    @Composable
    private fun Parent(visible: Boolean) {
        if (visible) {
            Box {
                Text(stringResource(R.string.viewers_count, "1"))
                LiveDot()
            }
        }
    }
}

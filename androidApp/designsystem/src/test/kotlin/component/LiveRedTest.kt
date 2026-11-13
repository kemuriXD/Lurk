package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.designsystem.theme.LiveRed
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveRedTest {
    @get:Rule val rule = createComposeRule()

    @After
    fun restoreAnimations() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun liveRedIsTheReferenceRed() {
        assertEquals(Color(0xFFE15241), LiveRed)
    }

    private fun dotCentre(
        mode: ThemeMode,
        amoled: Boolean = false,
        dynamicColor: Boolean = false,
    ): Color {
        // Animations off: the halo is fully faded, so the centre pixel is the dot alone.
        setAnimatorScale(0f)
        rule.setContent {
            LurkTheme(mode = mode, amoled = amoled, dynamicColor = dynamicColor) {
                LiveDot(Modifier.testTag(DOT), size = 20.dp, describe = false)
            }
        }
        val pixels = rule.onNodeWithTag(DOT).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun theDotIsLiveRedInLight() = assertEquals(LiveRed, dotCentre(ThemeMode.LIGHT))

    @Test
    fun theDotIsLiveRedInDark() = assertEquals(LiveRed, dotCentre(ThemeMode.DARK))

    @Test
    fun theDotIsLiveRedInAmoled() = assertEquals(LiveRed, dotCentre(ThemeMode.DARK, amoled = true))

    @Test
    fun theDotIsLiveRedWithDynamicColour() = assertEquals(LiveRed, dotCentre(ThemeMode.DARK, dynamicColor = true))

    @Test
    fun theAvatarLiveRingIsLiveRed() {
        rule.setContent {
            LurkTheme(mode = ThemeMode.DARK, dynamicColor = false) {
                LurkAvatar(null, "Streamer One", 48.dp, Modifier.testTag(DOT).size(48.dp), liveRing = true)
            }
        }
        val pixels = rule.onNodeWithTag(DOT).captureToImage().toPixelMap()
        // The ring hugs the edge: one pixel in from the left, halfway down.
        assertEquals(LiveRed, pixels[1, pixels.height / 2])
    }

    private companion object {
        const val DOT = "dot"
    }
}

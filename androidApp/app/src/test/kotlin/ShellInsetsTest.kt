package io.github.kemurixd.lurk

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShellInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var contentHeightPx by mutableIntStateOf(0)

    /** A bar of [BAR_DP] under the content, like the navigation bar, with a child that applies `imePadding()`. */
    private fun showBarOver(consume: Boolean) {
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).then(if (consume) Modifier.consumeBarInsets() else Modifier)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                            .onGloballyPositioned { contentHeightPx = it.size.height }
                            .testTag("content"),
                    )
                }
                Spacer(Modifier.height(BAR_DP.dp))
            }
        }
        compose.waitForIdle()
    }

    private fun showKeyboard(heightPx: Int) {
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()
    }

    private fun liftFromKeyboard(consume: Boolean): Int {
        showBarOver(consume)
        val before = contentHeightPx
        showKeyboard(KEYBOARD_PX)
        return before - contentHeightPx
    }

    @Test
    fun withoutConsumingTheBarTheKeyboardLiftsTheContentItsFullHeight() {
        assertEquals(KEYBOARD_PX, liftFromKeyboard(consume = false))
    }

    @Test
    fun childImePaddingAddsOnlyWhatTheBarDoesNotCover() {
        val barPx = with(Density(compose.activity)) { BAR_DP.dp.roundToPx() }
        assertEquals(KEYBOARD_PX - barPx, liftFromKeyboard(consume = true))
    }

    private companion object {
        const val BAR_DP = 80
        const val KEYBOARD_PX = 300
    }
}

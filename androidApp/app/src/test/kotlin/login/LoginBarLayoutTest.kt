package io.github.kemurixd.lurk.login

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The login title wraps instead of ellipsizing at 200% font on a 360 dp phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class LoginBarLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun theTitleIsNotEllipsizedAtDoubleFont() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                LurkTheme(dynamicColor = false) { LoginBar(Platform.TWITCH, onCancel = {}) }
            }
        }
        val out = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText("Sign in to Twitch")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(out)
        val layout = out.first()
        for (line in 0 until layout.lineCount) {
            assertTrue("title is ellipsized on line $line", !layout.isLineEllipsized(line))
        }
    }
}

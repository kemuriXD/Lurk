package io.github.kemurixd.lurk.featureui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.AccountRow
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An account row keeps its name and status readable on a 360 dp phone, whatever buttons it carries. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AccountRowLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        status: AccountStatus,
        fontScale: Float,
    ) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                LurkTheme(dynamicColor = false) {
                    Box(Modifier.width(360.dp).testTag("host")) {
                        AccountRowItem(AccountRow(Platform.KICK, status), onIntent = {})
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
        return out.first()
    }

    private fun assertWhole(
        text: String,
        scale: Float,
    ) {
        val layout = rule.onNodeWithText(text).layout()
        for (line in 0 until layout.lineCount) {
            assertTrue("\"$text\" at $scale is ellipsized on line $line", !layout.isLineEllipsized(line))
        }
        assertTrue(
            "\"$text\" at $scale is wider than its room",
            layout.size.width <= layout.layoutInput.constraints.maxWidth,
        )
    }

    @Test
    fun theSignedOutStatusWrapsInsteadOfEllipsizingAtDoubleFont() {
        show(AccountStatus.SignedOut, fontScale = 2f)
        assertWhole("Not signed in", 2f)
    }

    @Test
    fun anExpiredRowKeepsItsNameAndStatusReadable() {
        show(AccountStatus.Expired("Streamer One"), fontScale = 1f)
        assertWhole("Streamer One", 1f)
        assertEquals(1, rule.onNodeWithText("Streamer One").layout().lineCount)
        assertWhole("Session expired", 1f)
        assertEquals(1, rule.onNodeWithText("Session expired").layout().lineCount)
    }

    @Test
    fun anExpiredRowPutsBothButtonsBelowTheTextWithFullSizeTargets() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                Box(Modifier.width(360.dp)) {
                    AccountRowItem(AccountRow(Platform.KICK, AccountStatus.Expired("Streamer One")), onIntent = {})
                }
            }
        }
        val textBottom =
            rule
                .onNodeWithText("Session expired")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        for (tag in listOf(SettingsTestTags.signOut(Platform.KICK), SettingsTestTags.action(Platform.KICK))) {
            val box = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $box is not below the text ending at $textBottom", box.top >= textBottom)
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertTouchHeightIsEqualTo(48.dp)
        }
    }
}

package io.github.kemurixd.lurk.featureui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.AccountRow
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An account row's buttons stay inside it at 200% font on a 360 dp phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRowClippingTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun anExpiredRowKeepsBothButtonsOnScreenAtDoubleFont() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                LurkTheme(dynamicColor = false) {
                    Box(Modifier.width(360.dp).testTag("host")) {
                        AccountRowItem(AccountRow(Platform.KICK, AccountStatus.Expired("Streamer One")), onIntent = {})
                    }
                }
            }
        }
        val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        for (tag in listOf(SettingsTestTags.signOut(Platform.KICK), SettingsTestTags.action(Platform.KICK))) {
            val box = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $box leaves $host", box.left >= host.left && box.right <= host.right)
        }
    }
}

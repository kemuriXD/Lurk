package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CHIP = "error_chip"

/** Text and controls stay inside their containers, at the normal font size and at 200%, on a 360 dp phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClippingTest {
    @get:Rule val rule = createComposeRule()

    private var density = 1f

    private fun show(
        width: Dp,
        scale: Float,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            density = LocalDensity.current.density
            LurkTheme(dynamicColor = false) { LayoutHost(width, scale, content) }
        }
    }

    @Test
    fun categoryTileNameAndViewersStayInsideTheRoundedTile() = categoryTileStaysInside(1f)

    @Test
    fun categoryTileNameAndViewersStayInsideTheRoundedTileAtDoubleFont() = categoryTileStaysInside(2f)

    private fun categoryTileStaysInside(scale: Float) {
        run {
            val category = Category(Platform.TWITCH, "1", "Grand Theft Auto V", null).copy(viewers = 39_200)
            show(116.dp, scale) { CategoryTile(category, onClick = {}) }
            val host = rule.onNodeWithTag(HOST).bounds()
            val radius = LurkRadius.md.value * density
            assertInsideRounded(
                "name at ${scale}x",
                rule.onNodeWithText("Grand Theft Auto V", useUnmergedTree = true).bounds(),
                host,
                radius,
            )
            assertInsideRounded(
                "viewers at ${scale}x",
                rule.onNodeWithText("39.2K viewers", useUnmergedTree = true).bounds(),
                host,
                radius,
            )
        }
    }

    @Test
    fun streamCardTextStaysInsideTheCardAtPhoneWidth() = streamCardStaysInside(1f)

    @Test
    fun streamCardTextStaysInsideTheCardAtDoubleFont() = streamCardStaysInside(2f)

    private fun streamCardStaysInside(scale: Float) {
        run {
            show(Phone, scale) { StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {}) }
            val host = rule.onNodeWithTag(HOST).bounds()
            val radius = LurkRadius.lg.value * density
            for (text in listOf("Streamer One", "Speedrunning all night", "Retro Racers")) {
                assertInsideRounded(
                    "$text at ${scale}x",
                    rule.onNodeWithText(text, useUnmergedTree = true).bounds(),
                    host,
                    radius,
                )
            }
        }
    }

    @Test
    fun inlineErrorChipFitsAPhoneAtDoubleFont() {
        show(Phone, 2f) { InlineErrorChip(Platform.KICK, LurkError.Offline, onRetry = {}) }
        val host = rule.onNodeWithTag(HOST).bounds()
        for (text in listOf("Couldn’t load Kick", "Retry")) {
            val box = rule.onNodeWithText(text, useUnmergedTree = true).bounds()
            assertTrue("$text $box leaves $host", box.right <= host.right && box.left >= host.left)
        }
    }

    @Test
    fun inlineErrorChipReasonAndRetryStayInsideTheChipAtDoubleFont() {
        show(Phone, 2f) {
            InlineErrorChip(
                Platform.TWITCH,
                LurkError.Offline,
                onRetry = {},
                title = R.string.error_chip_more_message,
                modifier = Modifier.testTag(CHIP),
            )
        }
        val host = rule.onNodeWithTag(HOST).bounds()
        val chip = rule.onNodeWithTag(CHIP).bounds()
        assertTrue("chip $chip leaves $host", chip.left >= host.left && chip.right <= host.right)
        val reason = text(R.string.error_offline)
        val title = "Couldn’t load more from Twitch"
        for (label in listOf(title, reason, "Retry")) {
            val box = rule.onNodeWithText(label, useUnmergedTree = true).bounds()
            assertTrue(
                "$label $box leaves $chip",
                box.left >= chip.left && box.right <= chip.right && box.top >= chip.top && box.bottom <= chip.bottom,
            )
        }
    }

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun filterChipsCanAllBeReachedAtDoubleFont() {
        var selected = PlatformFilter.ALL
        show(Phone, 2f) { PlatformFilterChips(selected, onSelect = { selected = it }) }
        val host = rule.onNodeWithTag(HOST).bounds()
        rule.onNodeWithText("Kick", useUnmergedTree = true).performScrollTo()
        val kick = rule.onNodeWithText("Kick", useUnmergedTree = true).bounds()
        assertTrue("Kick chip $kick is outside $host", kick.right <= host.right && kick.left >= host.left)
    }

    @Test
    fun topBarTitleStaysInsideTheBarAtDoubleFont() {
        show(Phone, 2f) { LurkTopBar("Following", onAccount = {}) }
        val host = rule.onNodeWithTag(HOST).bounds()
        val title = rule.onNodeWithText("Following", useUnmergedTree = true).bounds()
        assertTrue("title $title is outside $host", title.top >= host.top && title.bottom <= host.bottom)
    }

    @Test
    fun emptyStateFitsAtDoubleFont() {
        show(Phone, 2f) { EmptyState("Nothing here yet", "Follow a channel to see it here.", "Browse" to {}) }
        val host = rule.onNodeWithTag(HOST).bounds()
        for (text in listOf("Nothing here yet", "Follow a channel to see it here.", "Browse")) {
            val box = rule.onNodeWithText(text, useUnmergedTree = true).bounds()
            assertTrue("$text $box leaves $host", box.left >= host.left && box.right <= host.right)
        }
    }
}

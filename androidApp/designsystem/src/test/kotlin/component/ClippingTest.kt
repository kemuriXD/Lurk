package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import io.github.kemurixd.lurk.designsystem.time.LocalClock
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
    fun expandedStreamCardStaysInsideAtPhoneWidth() = expandedStreamCardStaysInside(1f)

    @Test
    fun expandedStreamCardStaysInsideAtDoubleFont() = expandedStreamCardStaysInside(2f)

    private fun expandedStreamCardStaysInside(scale: Float) {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        val stream =
            testStream().copy(
                displayName = "A rather long display name",
                title = "A very long stream title that has to be cut short on a phone",
                category = Category(Platform.TWITCH, "1", "Grand Theft Auto V", null),
                viewers = 1_234_567,
                startedAt = now - 12.hours,
                tags = List(8) { "Tag number $it" },
            )
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalClock provides FixedClock(now)) {
                LurkTheme(dynamicColor = false) {
                    LayoutHost(Phone, scale) { ExpandedStreamCard(stream, onOpenPlayer = {}, onOpenChannel = {}) }
                }
            }
        }
        val host = rule.onNodeWithTag(HOST).bounds()
        val tags =
            listOf(
                ExpandedStreamCardTags.NAME,
                ExpandedStreamCardTags.TITLE,
                ExpandedStreamCardTags.UPTIME,
                ExpandedStreamCardTags.TAGS,
                ExpandedStreamCardTags.MORE_TAGS,
            )
        for (tag in tags) {
            val box = rule.onNodeWithTag(tag, useUnmergedTree = true).bounds()
            assertTrue(
                "$tag at ${scale}x $box leaves $host",
                box.left >= host.left && box.right <= host.right && box.top >= host.top && box.bottom <= host.bottom,
            )
        }
        val category = rule.onNodeWithText("Grand Theft Auto V", useUnmergedTree = true).bounds()
        assertTrue("category at ${scale}x $category leaves $host", category.right <= host.right)
        val badge = rule.onNodeWithContentDescription("Twitch", useUnmergedTree = true).bounds()
        assertTrue("badge at ${scale}x $badge leaves $host", badge.right <= host.right && badge.left >= host.left)
    }

    private class FixedClock(
        private val at: Instant,
    ) : Clock {
        override fun now(): Instant = at
    }

    /** Every label of an error surface lies inside the surface, which lies inside the host. */
    private fun assertErrorSurfaceFits(
        tag: String,
        labels: List<String>,
    ) {
        val host = rule.onNodeWithTag(HOST).bounds()
        val surface = rule.onNodeWithTag(tag).bounds()
        assertTrue("$tag $surface leaves $host", surface.left >= host.left && surface.right <= host.right)
        for (label in labels) {
            val box = rule.onNodeWithText(label, useUnmergedTree = true).bounds()
            assertTrue(
                "$label $box leaves $surface",
                box.left >= surface.left && box.right <= surface.right && box.top >= surface.top &&
                    box.bottom <= surface.bottom,
            )
            assertTrue("$label is squeezed to $box", box.width > 0f && box.height > 0f)
        }
    }

    @Test
    fun errorStateFitsAPhoneAtDoubleFont() {
        show(Phone, 2f) { ErrorHost(ErrorHandlers()) { ErrorState("Couldn’t load categories", LurkError.Offline, {}) } }
        assertErrorSurfaceFits(
            ErrorTags.STATE,
            listOf("Couldn’t load categories", text(R.string.error_offline), "Retry"),
        )
    }

    @Test
    fun errorBannerWithItsLongestActionFitsAPhoneAtDoubleFont() {
        show(Phone, 2f) {
            ErrorHost(ErrorHandlers()) {
                ErrorBanner("Couldn’t load Twitch", LurkError.ProxyFailed, {}, platform = Platform.TWITCH)
            }
        }
        assertErrorSurfaceFits(
            ErrorTags.BANNER,
            listOf("Couldn’t load Twitch", text(R.string.error_proxy_failed), "Open proxy settings"),
        )
    }

    @Test
    fun errorBannerFitsAPhoneAtNormalFont() {
        show(Phone, 1f) {
            ErrorHost(
                ErrorHandlers(),
            ) { ErrorBanner("Couldn’t load Kick", LurkError.Offline, {}, platform = Platform.KICK) }
        }
        assertErrorSurfaceFits(ErrorTags.BANNER, listOf("Couldn’t load Kick", text(R.string.error_offline), "Retry"))
    }

    @Test
    fun errorFooterFitsAPhoneAtDoubleFont() {
        show(Phone, 2f) { ErrorHost(ErrorHandlers()) { ErrorFooter(Platform.TWITCH, LurkError.Timeout, {}) } }
        assertErrorSurfaceFits(
            ErrorTags.FOOTER,
            listOf("Couldn’t load more from Twitch", text(R.string.error_timeout), "Retry"),
        )
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

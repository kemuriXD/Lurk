package io.github.kemurixd.lurk.featureui.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt
import kotlin.time.Instant

/** The channel page's text stays inside the rounded preview and the header, at normal and 200% font on 360 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelClippingTest {
    @get:Rule val rule = createComposeRule()

    private val ref = ChannelRef(Platform.TWITCH, "streamer1")
    private val category = Category(Platform.TWITCH, "509658", "Just Chatting", null)
    private val stream =
        Stream(ref, "1000001", "Streamer One", "Speedrun attempts all night", category, 12_400, null, null, null)
    private var density = 1f

    private fun inside(
        point: Offset,
        rect: Rect,
        radius: Float,
    ): Boolean {
        if (point.x !in rect.left..rect.right || point.y !in rect.top..rect.bottom) return false
        val dx = point.x - point.x.coerceIn(rect.left + radius, rect.right - radius)
        val dy = point.y - point.y.coerceIn(rect.top + radius, rect.bottom - radius)
        return sqrt(dx * dx + dy * dy) <= radius + 0.01f
    }

    private fun show(
        scale: Float,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                LurkTheme(dynamicColor = false) { Box(Modifier.width(360.dp).testTag("host")) { content() } }
            }
        }
    }

    private fun categoryLinkInsidePreview(scale: Float) {
        show(scale) { LivePreview(stream, "Streamer One", onClick = {}) }
        val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val link = rule.onNodeWithText("Just Chatting", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val radius = LurkRadius.lg.value * density
        val outside =
            listOf(
                link.topLeft,
                link.topRight,
                link.bottomLeft,
                link.bottomRight,
            ).filterNot { inside(it, host, radius) }
        assertTrue("category link $link pokes out of the rounded preview $host: $outside", outside.isEmpty())
    }

    @Test
    fun categoryLinkStaysInsideThePreview() = categoryLinkInsidePreview(1f)

    @Test
    fun categoryLinkStaysInsideThePreviewAtDoubleFont() = categoryLinkInsidePreview(2f)

    @Test
    fun headerTextStaysInsideTheHeaderAtDoubleFont() {
        val channel =
            Channel(ref, "1000001", "streamer1", "Streamer One", null, null, "", 48_200, stream, null)
        show(2f) {
            ChannelHeader(ref, channel, Instant.parse("2026-11-12T12:00:00Z"), onBack = {})
        }
        val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val status =
            rule
                .onNodeWithTag(
                    ChannelTestTags.STATUS,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue("status $status leaves the header $host", status.bottom <= host.bottom && status.right <= host.right)
    }
}

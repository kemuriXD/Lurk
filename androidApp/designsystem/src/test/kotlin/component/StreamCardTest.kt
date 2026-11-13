package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamCardTest {
    @get:Rule val rule = createComposeRule()

    private var player = 0
    private var channel = 0

    private fun show() =
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = { player++ }, onOpenChannel = { channel++ })
            }
        }

    private fun tap(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    @Test
    fun streamCardAvatarOpensChannel() {
        show()
        tap("stream_card_avatar")
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun streamCardNameOpensChannel() {
        show()
        tap("stream_card_name")
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun streamCardBodyOpensPlayer() {
        show()
        tap("stream_card_title")
        assertEquals(0 to 1, channel to player)
    }

    @Test
    fun channelControlsHaveAtLeast48dpTouchTargets() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
            }
        }
        // Compose widens small pointer targets to 48 dp; this pins that the layout keeps relying on it.
        val min = 48 * density - 0.5f
        val avatar =
            rule
                .onNodeWithTag(
                    "stream_card_avatar",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .touchBoundsInRoot
        assertTrue("avatar ${avatar.width}x${avatar.height}", avatar.width >= min && avatar.height >= min)
        val name = rule.onNodeWithTag("stream_card_name", useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
        assertTrue("name height ${name.height}", name.height >= min)
    }

    @Test
    fun placeholderCardNotClickable() {
        rule.setContent { LurkTheme(dynamicColor = false) { StreamCardPlaceholder() } }
        rule.onNodeWithTag("stream_card_placeholder", useUnmergedTree = true).assertHasNoClickAction()
    }
}

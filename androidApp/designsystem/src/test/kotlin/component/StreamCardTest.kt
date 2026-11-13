package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
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
    fun placeholderCardNotClickable() {
        rule.setContent { LurkTheme(dynamicColor = false) { StreamCardPlaceholder() } }
        rule.onNodeWithTag("stream_card_placeholder", useUnmergedTree = true).assertHasNoClickAction()
    }
}

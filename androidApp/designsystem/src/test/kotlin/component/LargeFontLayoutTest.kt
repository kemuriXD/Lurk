package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** At 200% font on a 360 dp phone, overlays stay on one line and headers keep their words whole. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LargeFontLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.TWITCH, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Speedrunning all night",
            category = null,
            viewers = 12_400,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )

    @Test
    fun theViewerBadgeIsOneLineAtDoubleFont() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                LayoutHost(Phone, 2f) { StreamCard(stream(), onOpenPlayer = {}, onOpenChannel = {}) }
            }
        }
        // One line when under 45 dp tall: a line of 11 sp at no more than 1.3x, its 4 dp of padding and the 6 dp inset
        // around it. Two lines, or one line at 2x, are 48 dp and more.
        val badge = rule.onNodeWithTag(StreamCardTags.VIEWERS, useUnmergedTree = true).bounds()
        assertTrue("badge is ${badge.height} px tall", badge.height < 45f)
    }

    @Test
    fun theLiveChannelsHeaderKeepsItsWordsWholeBesideTheSortButton() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                LayoutHost(Phone, 2f) {
                    SectionHeader("Live channels") {
                        SortMenu(
                            selected = "Viewers",
                            options = listOf("Viewers", "Recent"),
                            label = { it },
                            description = { "Sort: $it" },
                            onSelect = {},
                        )
                    }
                }
            }
        }
        assertWrapsWholeWords("Live channels", rule.onNodeWithText("Live channels").textLayout())
    }
}

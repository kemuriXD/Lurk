package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelRowTest {
    @get:Rule val rule = createComposeRule()

    private fun liveChannel() =
        Channel(
            ref = ChannelRef(Platform.TWITCH, "streamer1"),
            id = "1000001",
            chatId = "streamer1",
            displayName = "Streamer One",
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = testStream(),
            lastStream = null,
        )

    @Test
    fun liveChannelShowsRingByDefault() {
        rule.setContent { LurkTheme(dynamicColor = false) { ChannelRow(liveChannel(), onClick = {}) } }
        rule.onAllNodesWithTag(AvatarTags.LIVE_RING, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun ringCanBeTurnedOff() {
        rule.setContent {
            LurkTheme(dynamicColor = false) { ChannelRow(liveChannel(), onClick = {}, showLiveRing = false) }
        }
        rule.onAllNodesWithTag(AvatarTags.LIVE_RING, useUnmergedTree = true).assertCountEquals(0)
    }
}

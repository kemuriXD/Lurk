package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelRowTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")

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

    private fun offlineChannel(lastLiveAt: Instant?) =
        liveChannel().copy(live = null, lastStream = lastLiveAt?.let { LastStream(null, null, it) })

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

    @Test
    fun offlineChannelSaysWhenItWasLastLive() {
        rule.setContent {
            LurkTheme(dynamicColor = false) { ChannelRow(offlineChannel(now - 3.days), onClick = {}, now = { now }) }
        }
        rule.onNodeWithText("Offline · last live 3 days ago").assertExists()
    }

    @Test
    fun offlineChannelWithoutATimeIsJustOffline() {
        rule.setContent {
            LurkTheme(dynamicColor = false) { ChannelRow(offlineChannel(null), onClick = {}, now = { now }) }
        }
        rule.onNodeWithText("Offline").assertExists()
    }

    @Test
    fun liveChannelIgnoresItsLastBroadcast() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ChannelRow(
                    liveChannel().copy(lastStream = LastStream(null, null, now - 3.days)),
                    onClick = {},
                    now = { now },
                )
            }
        }
        rule.onNodeWithText("Retro Racers · 12.4K viewers").assertExists()
    }
}

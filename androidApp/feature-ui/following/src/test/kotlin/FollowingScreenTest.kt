package io.github.kemurixd.lurk.featureui.following

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.following.FollowingIntent
import io.github.kemurixd.lurk.feature.following.FollowingSort
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowingScreenTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<FollowingIntent>()
    private val players = mutableListOf<Stream>()
    private val channels = mutableListOf<ChannelRef>()
    private val signIns = mutableListOf<Platform>()

    private fun show(state: FollowingUiState) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                FollowingScreen(
                    state = state,
                    onIntent = { intents += it },
                    onOpenPlayer = { players += it },
                    onOpenChannel = { channels += it },
                    onSignIn = { signIns += it },
                )
            }
        }
    }

    @Test
    fun liveSectionBeforeOffline() {
        show(loaded(live = listOf(stream("streamer1")), offline = listOf(channel("sleeper1"))))
        val live =
            rule
                .onNode(isHeading() and hasText("Live"))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val offline =
            rule
                .onNode(isHeading() and hasText("Offline"))
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("Live ($live) should sit above Offline ($offline)", live < offline)
    }

    @Test
    fun offlineRowOpensChannel() {
        show(loaded(live = emptyList(), offline = listOf(channel("sleeper1"))))
        rule.onNodeWithText("Sleeper1").performClick()
        assertEquals(listOf(ChannelRef(Platform.TWITCH, "sleeper1")), channels)
    }

    @Test
    fun liveCardOpensPlayer() {
        val stream = stream("streamer1")
        show(loaded(live = listOf(stream), offline = emptyList()))
        rule.onNodeWithTag("stream_card").performClick()
        assertEquals(listOf(stream), players)
    }

    @Test
    fun signedOutShowsSignInForEachPlatform() {
        show(
            state(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.NotSignedIn,
                        Platform.KICK to PlatformSlice.NotSignedIn,
                    ),
            ),
        )
        rule.onNodeWithText("Sign in to Twitch").performClick()
        rule.onNodeWithText("Sign in to Kick").performClick()
        assertEquals(listOf(Platform.TWITCH, Platform.KICK), signIns)
    }

    @Test
    fun partialFailureShowsChipAndOtherList() {
        show(
            state(
                live = listOf(stream("streamer1")),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Followed(listOf(stream("streamer1")), emptyList())),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                    ),
            ),
        )
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
        rule.onNodeWithTag("stream_card").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(listOf<FollowingIntent>(FollowingIntent.Retry(Platform.KICK)), intents)
    }

    @Test
    fun loadingShowsPlaceholders() {
        show(
            state(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loading,
                        Platform.KICK to PlatformSlice.Loading,
                    ),
            ),
        )
        rule.onAllNodesWithTag("stream_card_placeholder")[0].assertIsDisplayed()
    }

    @Test
    fun sortMenuSendsIntent() {
        show(loaded(live = listOf(stream("streamer1")), offline = emptyList()))
        rule.onNodeWithContentDescription("Sort live channels, currently by Viewers").performClick()
        rule.onNodeWithText("A–Z").performClick()
        assertEquals(listOf<FollowingIntent>(FollowingIntent.SelectSort(FollowingSort.NAME)), intents)
    }

    @Test
    fun pullToRefreshSendsRefresh() {
        show(loaded(live = listOf(stream("streamer1")), offline = emptyList()))
        rule.onNodeWithTag("following_list").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(listOf<FollowingIntent>(FollowingIntent.Refresh), intents)
    }

    @Test
    fun duplicateLoginAcrossPlatformsRendersBoth() {
        val twitch = stream("streamer1", Platform.TWITCH)
        val kick = stream("streamer1", Platform.KICK)
        show(loaded(live = listOf(twitch, kick), offline = emptyList()))
        rule.onAllNodesWithTag("stream_card").assertCountEquals(2)
    }

    private fun loaded(
        live: List<Stream>,
        offline: List<Channel>,
    ) = state(
        live = live,
        offline = offline,
        slices =
            mapOf(
                Platform.TWITCH to PlatformSlice.Loaded(Followed(live, offline)),
                Platform.KICK to PlatformSlice.Loaded(Followed(emptyList(), emptyList())),
            ),
    )

    private fun state(
        live: List<Stream> = emptyList(),
        offline: List<Channel> = emptyList(),
        slices: Map<Platform, PlatformSlice<Followed>>,
    ) = FollowingUiState(
        filter = PlatformFilter.ALL,
        sort = FollowingSort.VIEWERS,
        live = live,
        offline = offline,
        slices = slices,
        refreshing = false,
    )

    private fun stream(
        login: String,
        platform: Platform = Platform.TWITCH,
    ) = Stream(
        channel = ChannelRef(platform, login),
        channelId = "1000001",
        displayName = login.replaceFirstChar { it.uppercase() },
        title = "Speedrunning all night",
        category = null,
        viewers = 1_200,
        startedAt = null,
        thumbnailUrl = null,
        avatarUrl = null,
    )

    private fun channel(login: String) =
        Channel(
            ref = ChannelRef(Platform.TWITCH, login),
            id = "1000002",
            chatId = login,
            displayName = login.replaceFirstChar { it.uppercase() },
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = null,
            lastStream = null,
        )
}

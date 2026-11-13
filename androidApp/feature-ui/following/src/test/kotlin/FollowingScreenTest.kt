package io.github.kemurixd.lurk.featureui.following

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.SectionHeaderTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.time.LocalClock
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowingScreenTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<FollowingIntent>()
    private val players = mutableListOf<Stream>()
    private val channels = mutableListOf<ChannelRef>()
    private val signIns = mutableListOf<Platform>()
    private var browseTaps = 0
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun show(state: FollowingUiState) {
        rule.setContent {
            CompositionLocalProvider(LocalClock provides fixedClock) {
                LurkTheme(dynamicColor = false) {
                    FollowingScreen(
                        state = state,
                        onIntent = { intents += it },
                        onOpenPlayer = { players += it },
                        onOpenChannel = { channels += it },
                        onSignIn = { signIns += it },
                        onOpenBrowse = { browseTaps++ },
                    )
                }
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
    fun offlineRowSaysWhenItWasLastLive() {
        val sleeper = channel("sleeper1").copy(lastStream = LastStream(null, null, now - 3.days))
        show(loaded(live = emptyList(), offline = listOf(sleeper, channel("sleeper2"))))
        rule.onNodeWithText("Offline · last live 3 days ago").assertIsDisplayed()
    }

    @Test
    fun offlineRowsWithoutATimeAskForItWhenShown() {
        val sleeper = channel("sleeper1").copy(lastStream = LastStream(null, null, now - 3.days))
        show(loaded(live = emptyList(), offline = listOf(sleeper, channel("sleeper2"))))
        rule.onNodeWithText("Sleeper1").assertExists()
        // The "nobody live" block leaves room for one row on the test screen, so the second one is scrolled in.
        rule.onNodeWithTag(FollowingTestTags.LIST).performScrollToNode(hasText("Sleeper2"))
        rule.waitForIdle()
        assertEquals(
            listOf<FollowingIntent>(FollowingIntent.OfflineRowShown(ChannelRef(Platform.TWITCH, "sleeper2"))),
            intents,
        )
    }

    @Test
    fun onlyOfflineRowsOnScreenAskForTheirTime() {
        val many = (1..100).map { channel("sleeper$it") }
        show(loaded(live = emptyList(), offline = many))
        rule.waitForIdle()
        val asked = intents.filterIsInstance<FollowingIntent.OfflineRowShown>().map { it.channel.login }
        assertTrue("the first row asks", "sleeper1" in asked)
        assertTrue("rows far below the screen don't ask, asked: $asked", "sleeper100" !in asked)
        assertTrue("fewer rows ask than exist: ${asked.size}", asked.size < many.size)
    }

    @Test
    fun offlineRowsThatScrollAwayLetGoOfTheirLookup() {
        val many = (1..100).map { channel("sleeper$it") }
        show(loaded(live = emptyList(), offline = many))
        rule.onNodeWithTag(FollowingTestTags.LIST).performScrollToNode(hasText("Sleeper100"))
        rule.waitForIdle()
        val hidden = intents.filterIsInstance<FollowingIntent.OfflineRowHidden>().map { it.channel.login }
        assertTrue("the first row lets go once scrolled away, hidden: $hidden", "sleeper1" in hidden)
        assertTrue("the row on screen keeps its lookup", "sleeper100" !in hidden)
    }

    @Test
    fun sectionHeadersShowCounts() {
        show(
            loaded(
                live = listOf(stream("streamer1"), stream("streamer2")),
                offline = listOf(channel("sleeper1")),
            ),
        )
        val counts = rule.onAllNodesWithTag(SectionHeaderTags.COUNT, useUnmergedTree = true)
        counts.assertCountEquals(2)
        counts[0].assertTextEquals("2")
        counts[1].assertTextEquals("1")
    }

    @Test
    fun nobodyLiveOffersBrowse() {
        show(loaded(live = emptyList(), offline = listOf(channel("sleeper1"))))
        rule.onNodeWithText("Browse live channels").performClick()
        assertEquals(1, browseTaps)
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
        rule.onAllNodesWithTag("stream_card").assertCountEquals(0)
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
        rule.onNodeWithTag(FollowingTestTags.LIST).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(listOf<FollowingIntent>(FollowingIntent.Refresh), intents)
    }

    @Test
    fun openingTheSortMenuSendsNothing() {
        show(loaded(live = listOf(stream("streamer1")), offline = emptyList()))
        rule.onNodeWithContentDescription("Sort live channels, currently by Viewers").performClick()
        rule.onNodeWithText("A–Z").assertIsDisplayed()
        rule.waitForIdle()
        assertEquals(emptyList<FollowingIntent>(), intents)
    }

    @Test
    fun showingTheScreenSendsNothing() {
        show(loaded(live = listOf(stream("streamer1")), offline = emptyList()))
        rule.waitForIdle()
        assertEquals(emptyList<FollowingIntent>(), intents)
    }

    @Test
    fun aQuietReloadOverItemsShowsNoSpinnerAndNoPlaceholders() {
        show(loaded(live = listOf(stream("streamer1")), offline = emptyList()).copy(refreshing = true))
        rule.onNodeWithTag(FollowingTestTags.REFRESH_INDICATOR).assertDoesNotExist()
        rule.onAllNodesWithTag("stream_card_placeholder").assertCountEquals(0)
        rule.onAllNodesWithTag("stream_card").assertCountEquals(1)
    }

    @Test
    fun aReloadTheUserAskedForShowsTheSpinner() {
        show(
            loaded(
                live = listOf(stream("streamer1")),
                offline = emptyList(),
            ).copy(refreshing = true, manualRefresh = true),
        )
        rule.onNodeWithTag(FollowingTestTags.REFRESH_INDICATOR).assertIsDisplayed()
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

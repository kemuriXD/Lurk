package io.github.kemurixd.lurk.featureui.following

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.component.ErrorTags
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCardTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.following.FollowingSort
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Following shows while a failed platform is asked again, and during a pull to refresh over a banner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowingRetryScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: FollowingUiState) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    FollowingScreen(
                        state = state,
                        onIntent = {},
                        onOpenPlayer = {},
                        onOpenChannel = {},
                        onSignIn = {},
                        onOpenBrowse = {},
                    )
                }
            }
        }
    }

    private fun state(
        live: List<Stream> = emptyList(),
        slices: Map<Platform, PlatformSlice<Followed>>,
    ) = FollowingUiState(
        filter = PlatformFilter.ALL,
        sort = FollowingSort.VIEWERS,
        live = live,
        offline = emptyList(),
        slices = slices,
        refreshing = false,
    )

    private fun stream(login: String) =
        Stream(
            channel = ChannelRef(Platform.TWITCH, login),
            channelId = "1000001",
            displayName = login.replaceFirstChar { it.uppercase() },
            title = "Speedrunning all night",
            category = null,
            viewers = 1_200,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )

    private fun failedKick(
        retrying: Set<Platform> = emptySet(),
        manual: Boolean = false,
    ): FollowingUiState {
        val twitch = stream("streamer1")
        return state(
            live = listOf(twitch),
            slices =
                mapOf(
                    Platform.TWITCH to PlatformSlice.Loaded(Followed(listOf(twitch), emptyList())),
                    Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                ),
        ).copy(retrying = retrying, refreshing = manual || retrying.isNotEmpty(), manualRefresh = manual)
    }

    @Test
    fun aRetryingBannerStaysOverTheListAndItsRetryIsOff() {
        show(failedKick(retrying = setOf(Platform.KICK)))
        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onAllNodesWithTag(ExpandedStreamCardTags.CARD).assertCountEquals(1)
        rule.onAllNodesWithTag(ExpandedStreamCardTags.PLACEHOLDER).assertCountEquals(0)
        rule.onNodeWithContentDescription("Retry loading Kick").assertIsNotEnabled()
    }

    @Test
    fun aPullToRefreshOverABannerKeepsItAndShowsOnlyThePullSpinner() {
        show(failedKick(manual = true))
        rule.onNodeWithTag(FollowingTestTags.REFRESH_INDICATOR).assertIsDisplayed()
        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").assertIsEnabled()
        rule.onAllNodesWithTag(ErrorTags.RETRY_PROGRESS).assertCountEquals(0)
        rule.onAllNodesWithTag(ExpandedStreamCardTags.CARD).assertCountEquals(1)
        rule.onAllNodesWithTag(ExpandedStreamCardTags.PLACEHOLDER).assertCountEquals(0)
    }

    @Test
    fun aRetryingFullScreenErrorStaysAndItsRetryIsOff() {
        show(
            state(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                    ),
            ).copy(retrying = setOf(Platform.TWITCH, Platform.KICK), refreshing = true),
        )
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsNotEnabled()
        rule.onAllNodesWithTag(ExpandedStreamCardTags.PLACEHOLDER).assertCountEquals(0)
    }
}

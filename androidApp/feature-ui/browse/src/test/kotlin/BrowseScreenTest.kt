package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.component.ErrorTags
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCardTags
import io.github.kemurixd.lurk.designsystem.component.PlatformTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.browse.CategoriesIntent
import io.github.kemurixd.lurk.feature.browse.CategoriesUiState
import io.github.kemurixd.lurk.feature.browse.TopLiveIntent
import io.github.kemurixd.lurk.feature.browse.TopLiveUiState
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowseScreenTest {
    @get:Rule val rule = createComposeRule()

    private val topLiveIntents = mutableListOf<TopLiveIntent>()
    private val categoryIntents = mutableListOf<CategoriesIntent>()
    private val players = mutableListOf<Stream>()
    private val channels = mutableListOf<ChannelRef>()
    private val opened = mutableListOf<Category>()
    private val signIns = mutableListOf<Platform>()

    /** Bumping it recomposes the screen with fresh callbacks, without changing the state. */
    private val recomposeTick = mutableIntStateOf(0)

    private fun show(
        topLive: TopLiveUiState = topLiveState(),
        categories: CategoriesUiState = categoriesState(),
    ) {
        rule.setContent {
            val tick = recomposeTick.intValue
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers(signIn = { signIns += it })) {
                    BrowseScreen(
                        topLive = topLive,
                        onTopLiveIntent = { if (tick >= 0) topLiveIntents += it },
                        categories = categories,
                        onCategoriesIntent = { categoryIntents += it },
                        onOpenPlayer = { players += it },
                        onOpenChannel = { channels += it },
                        onOpenCategory = { opened += it },
                    )
                }
            }
        }
    }

    @Test
    fun topLiveIsTheDefaultTab() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        rule.onNodeWithText("Streamer One").assertIsDisplayed()
    }

    @Test
    fun streamCardOpensPlayer() {
        val stream = stream()
        show(topLive = topLiveState(streams = listOf(stream)))
        rule.onNodeWithTag("stream_card").performClick()
        assertEquals(listOf(stream), players)
    }

    @Test
    fun topLiveKeepsTheListCard() {
        show(topLive = topLiveState(streams = listOf(stream())))
        rule.onNodeWithTag("stream_card").assertIsDisplayed()
        rule.onNodeWithTag(ExpandedStreamCardTags.CARD).assertDoesNotExist()
    }

    @Test
    fun filterChipSendsIntent() {
        show()
        rule.onNodeWithText("Kick").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.SelectFilter(PlatformFilter.KICK)), topLiveIntents)
    }

    @Test
    fun failedPlatformShowsABannerThatRetries() {
        show(
            topLive =
                topLiveState(
                    slices =
                        mapOf(
                            Platform.TWITCH to PlatformSlice.Loaded(Unit),
                            Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                        ),
                ),
        )
        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithTag(ErrorTags.STATE).assertDoesNotExist()
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.Retry(Platform.KICK)), topLiveIntents)
    }

    @Test
    fun firstPageBannerKeepsRetryForAnErrorRetryingCannotFix() {
        show(
            topLive =
                topLiveState(
                    slices =
                        mapOf(
                            Platform.TWITCH to PlatformSlice.Loaded(Unit),
                            Platform.KICK to PlatformSlice.Failed(LurkError.IntegrityRequired),
                        ),
                ),
        )
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.Retry(Platform.KICK)), topLiveIntents)
    }

    @Test
    fun loadMoreFooterHidesRetryWhenRetryingCannotHelp() {
        show(topLive = topLiveState(loadMoreErrors = mapOf(Platform.TWITCH to LurkError.IntegrityRequired)))
        rule.onNodeWithTag(ErrorTags.FOOTER).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load more from Twitch").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Twitch").assertDoesNotExist()
    }

    @Test
    fun loadMoreFooterRetriesWhenRetryingCanHelp() {
        show(topLive = topLiveState(loadMoreErrors = mapOf(Platform.TWITCH to LurkError.Timeout)))
        rule.onNodeWithContentDescription("Retry loading Twitch").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.Retry(Platform.TWITCH)), topLiveIntents)
    }

    @Test
    fun everyPlatformFailingShowsOneErrorStateThatRetriesBoth() {
        show(
            topLive =
                topLiveState(
                    streams = emptyList(),
                    slices =
                        mapOf(
                            Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                            Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                        ),
                ),
        )
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithTag(ErrorTags.BANNER).assertDoesNotExist()
        rule.onNodeWithText("Couldn’t load live channels").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(
            listOf<TopLiveIntent>(TopLiveIntent.Retry(Platform.TWITCH), TopLiveIntent.Retry(Platform.KICK)),
            topLiveIntents,
        )
    }

    @Test
    fun endOfListTriggersLoadMore() {
        show(topLive = topLiveState(streams = listOf(stream()), canLoadMore = true))
        rule.waitForIdle()
        repeat(3) {
            recomposeTick.intValue++
            rule.waitForIdle()
        }
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.LoadMore), topLiveIntents)
    }

    @Test
    fun noLoadMoreWhenThereIsNothingMore() {
        show(topLive = topLiveState(streams = listOf(stream()), canLoadMore = false))
        rule.waitForIdle()
        assertEquals(emptyList<TopLiveIntent>(), topLiveIntents)
    }

    @Test
    fun categoriesTabShowsTilesThatOpenTheCategory() {
        val category = category("1")
        show(categories = categoriesState(categories = listOf(category)))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithTag(BrowseTestTags.CATEGORIES_LIST).assertIsDisplayed()
        rule.onNodeWithText("Category 1").performClick()
        assertEquals(listOf(category), opened)
    }

    @Test
    fun categoryTileOpensCategory() {
        val category = category("2")
        show(categories = categoriesState(categories = listOf(category)))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithText("Category 2").performClick()
        assertEquals(listOf(category), opened)
    }

    @Test
    fun segmentedSwitchChangesPlatform() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithTag(BrowseTestTags.PLATFORM_KICK).performClick()
        assertEquals(listOf<CategoriesIntent>(CategoriesIntent.SelectPlatform(Platform.KICK)), categoryIntents)
    }

    @Test
    fun failedCategoriesRetry() {
        show(categories = categoriesState(slice = PlatformSlice.Failed(LurkError.Offline)))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load categories").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(listOf<CategoriesIntent>(CategoriesIntent.Retry), categoryIntents)
    }

    @Test
    fun signedOutCategoriesOfferSignInInsteadOfLookingEmpty() {
        show(categories = categoriesState(slice = PlatformSlice.NotSignedIn))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("No categories yet").assertDoesNotExist()
        rule.onNodeWithText("Sign in").performClick()
        assertEquals(listOf(Platform.TWITCH), signIns)
    }

    @Test
    fun categoriesLoadMoreFailureShowsTheFooter() {
        show(categories = categoriesState(categories = listOf(category("1")), loadMoreError = LurkError.Timeout))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithTag(ErrorTags.FOOTER).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Twitch").performClick()
        assertEquals(listOf<CategoriesIntent>(CategoriesIntent.Retry), categoryIntents)
    }

    private fun badges() = rule.onAllNodesWithTag(PlatformTags.BADGE, useUnmergedTree = true)

    private fun logos(platform: Platform) = rule.onAllNodesWithTag(PlatformTags.logo(platform), useUnmergedTree = true)

    @Test
    fun topLiveShowsBadgesUnderTheAllFilter() {
        show(topLive = topLiveState())
        badges().assertCountEquals(1)
    }

    @Test
    fun topLiveHidesBadgesUnderASinglePlatformFilter() {
        show(topLive = topLiveState().copy(filter = PlatformFilter.TWITCH))
        badges().assertCountEquals(0)
    }

    @Test
    fun topLiveChipsLeadWithTheirLogos() {
        show()
        logos(Platform.TWITCH).assertCountEquals(1)
        logos(Platform.KICK).assertCountEquals(1)
    }

    @Test
    fun categoryTilesHideTheirBadgeUnderThePlatformSwitch() {
        show(categories = categoriesState(categories = listOf(category("1"))))
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        rule.onNodeWithText("Category 1").assertIsDisplayed()
        badges().assertCountEquals(0)
    }

    @Test
    fun platformSwitchSegmentsLeadWithTheirLogos() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick()
        logos(Platform.TWITCH).assertCountEquals(1)
        logos(Platform.KICK).assertCountEquals(1)
    }

    private fun topLiveState(
        streams: List<Stream> = listOf(stream()),
        slices: Map<Platform, PlatformSlice<Unit>> =
            mapOf(Platform.TWITCH to PlatformSlice.Loaded(Unit), Platform.KICK to PlatformSlice.Loaded(Unit)),
        canLoadMore: Boolean = false,
        loadMoreErrors: Map<Platform, LurkError> = emptyMap(),
    ) = TopLiveUiState(
        filter = PlatformFilter.ALL,
        streams = streams,
        slices = slices,
        canLoadMore = canLoadMore,
        loadingMore = false,
        loadMoreErrors = loadMoreErrors,
    )

    private fun categoriesState(
        categories: List<Category> = emptyList(),
        slice: PlatformSlice<Unit> = PlatformSlice.Loaded(Unit),
        loadMoreError: LurkError? = null,
    ) = CategoriesUiState(
        platform = Platform.TWITCH,
        categories = categories,
        slice = slice,
        canLoadMore = false,
        loadMoreError = loadMoreError,
    )

    private fun category(id: String) = Category(Platform.TWITCH, id, "Category $id", boxArtUrl = null, viewers = 1200)

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.TWITCH, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Test stream",
            category = null,
            viewers = 900,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )
}

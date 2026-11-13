package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
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

    private fun show(
        topLive: TopLiveUiState = topLiveState(),
        categories: CategoriesUiState = categoriesState(),
    ) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                BrowseScreen(
                    topLive = topLive,
                    onTopLiveIntent = { topLiveIntents += it },
                    categories = categories,
                    onCategoriesIntent = { categoryIntents += it },
                    onOpenPlayer = { players += it },
                    onOpenChannel = { channels += it },
                    onOpenCategory = { opened += it },
                )
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
    fun filterChipSendsIntent() {
        show()
        rule.onNodeWithText("Kick").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.SelectFilter(PlatformFilter.KICK)), topLiveIntents)
    }

    @Test
    fun failedPlatformShowsChipThatRetries() {
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
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.Retry(Platform.KICK)), topLiveIntents)
    }

    @Test
    fun endOfListTriggersLoadMore() {
        show(topLive = topLiveState(streams = listOf(stream()), canLoadMore = true))
        rule.waitForIdle()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.LoadMore), topLiveIntents.distinct())
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
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf<CategoriesIntent>(CategoriesIntent.Retry), categoryIntents)
    }

    private fun topLiveState(
        streams: List<Stream> = listOf(stream()),
        slices: Map<Platform, PlatformSlice<Unit>> =
            mapOf(Platform.TWITCH to PlatformSlice.Loaded(Unit), Platform.KICK to PlatformSlice.Loaded(Unit)),
        canLoadMore: Boolean = false,
    ) = TopLiveUiState(
        filter = PlatformFilter.ALL,
        streams = streams,
        slices = slices,
        canLoadMore = canLoadMore,
        loadingMore = false,
    )

    private fun categoriesState(
        categories: List<Category> = emptyList(),
        slice: PlatformSlice<Unit> = PlatformSlice.Loaded(Unit),
    ) = CategoriesUiState(
        platform = Platform.TWITCH,
        categories = categories,
        slice = slice,
        canLoadMore = false,
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

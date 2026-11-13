package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsIntent
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsUiState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryScreenTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<CategoryStreamsIntent>()
    private val players = mutableListOf<Stream>()
    private var backs = 0

    /** Bumping it recomposes the screen with fresh callbacks, without changing the state. */
    private val recomposeTick = mutableIntStateOf(0)

    private fun show(
        state: CategoryStreamsUiState,
        platform: Platform = Platform.KICK,
    ) {
        rule.setContent {
            val tick = recomposeTick.intValue
            LurkTheme(dynamicColor = false) {
                CategoryScreen(
                    platform = platform,
                    name = "Cozy Builder",
                    state = state,
                    onIntent = { if (tick >= 0) intents += it },
                    onOpenPlayer = { players += it },
                    onOpenChannel = {},
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun headerNamesTheCategoryAndListsStreams() {
        show(state(streams = listOf(stream())))
        rule.onNodeWithText("Cozy Builder").assertIsDisplayed()
        rule.onNodeWithText("Streamer One").assertIsDisplayed()
    }

    @Test
    fun streamCardOpensPlayer() {
        val stream = stream()
        show(state(streams = listOf(stream)))
        rule.onNodeWithTag("stream_card").performClick()
        assertEquals(listOf(stream), players)
    }

    @Test
    fun endOfListTriggersLoadMore() {
        show(state(streams = listOf(stream()), canLoadMore = true))
        rule.waitForIdle()
        repeat(3) {
            recomposeTick.intValue++
            rule.waitForIdle()
        }
        assertEquals(listOf<CategoryStreamsIntent>(CategoryStreamsIntent.LoadMore), intents)
    }

    @Test
    fun notFoundOffersGoingBack() {
        show(state(slice = PlatformSlice.Failed(LurkError.NotFound)))
        rule.onNodeWithText("Nothing found here.").assertIsDisplayed()
        rule.onNodeWithText("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun otherFailuresOfferRetry() {
        show(state(slice = PlatformSlice.Failed(LurkError.Offline)))
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf<CategoryStreamsIntent>(CategoryStreamsIntent.Retry), intents)
    }

    @Test
    fun sortMenuShowsTheSortInUseAndSendsTheNewOne() {
        show(
            state(
                streams = listOf(stream()),
                sorts = listOf(CategorySort.FEATURED, CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH),
                sort = CategorySort.VIEWERS_HIGH,
            ),
        )
        rule.onNodeWithContentDescription("Sort live channels, currently by Viewers (high to low)").performClick()
        rule.onNodeWithText("Recommended").assertIsDisplayed()
        rule.onNodeWithText("Featured").performClick()

        assertEquals(listOf<CategoryStreamsIntent>(CategoryStreamsIntent.SelectSort(CategorySort.FEATURED)), intents)
    }

    @Test
    fun twitchSortsUseTwitchsNames() {
        show(
            state(
                sorts =
                    listOf(
                        CategorySort.RECOMMENDED,
                        CategorySort.VIEWERS_HIGH,
                        CategorySort.VIEWERS_LOW,
                        CategorySort.RECENT,
                    ),
                sort = CategorySort.RECOMMENDED,
            ),
            platform = Platform.TWITCH,
        )
        rule.onNodeWithContentDescription("Sort live channels, currently by Recommended for you").performClick()
        rule.onNodeWithText("Viewers (low to high)").assertIsDisplayed()
        rule.onNodeWithText("Recently started").assertIsDisplayed()
    }

    @Test
    fun sortMenuShowsWhileTheFirstPageLoads() {
        show(
            state(
                slice = PlatformSlice.Loading,
                sorts = listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH),
                sort = CategorySort.FEATURED,
            ),
        )
        rule.onNodeWithContentDescription("Sort live channels, currently by Featured").assertIsDisplayed()
    }

    @Test
    fun noSortMenuWithoutAChoice() {
        show(
            state(
                streams = listOf(stream()),
                sorts = listOf(CategorySort.VIEWERS_HIGH),
                sort = CategorySort.VIEWERS_HIGH,
            ),
        )
        rule.onNodeWithText("Viewers (high to low)").assertDoesNotExist()
    }

    private fun state(
        streams: List<Stream> = emptyList(),
        slice: PlatformSlice<Unit> = PlatformSlice.Loaded(Unit),
        canLoadMore: Boolean = false,
        sorts: List<CategorySort> = emptyList(),
        sort: CategorySort? = null,
    ) = CategoryStreamsUiState(
        streams = streams,
        slice = slice,
        canLoadMore = canLoadMore,
        loadingMore = false,
        sorts = sorts,
        sort = sort,
    )

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.KICK, "streamer1"),
            channelId = "",
            displayName = "Streamer One",
            title = "Test stream",
            category = null,
            viewers = 900,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )
}

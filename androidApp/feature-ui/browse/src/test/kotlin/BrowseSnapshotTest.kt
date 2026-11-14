package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.browse.CategoriesUiState
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsUiState
import io.github.kemurixd.lurk.feature.browse.TopLiveUiState
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** Baselines for Browse (Top Live, Categories) and Category detail, named after the Figma frames. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class BrowseSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val bothLoaded =
        mapOf<Platform, PlatformSlice<Unit>>(
            Platform.TWITCH to PlatformSlice.Loaded(Unit),
            Platform.KICK to PlatformSlice.Loaded(Unit),
        )

    private fun topLive(
        name: String,
        state: TopLiveUiState,
        largeFont: Boolean = false,
    ) = rule.snapshots(name, largeFont) {
        BrowseScreen(
            topLive = state,
            onTopLiveIntent = {},
            categories = categoriesState(),
            onCategoriesIntent = {},
            onOpenPlayer = {},
            onOpenChannel = {},
            onOpenCategory = {},
        )
    }

    private fun categories(
        name: String,
        state: CategoriesUiState,
        largeFont: Boolean = false,
    ) = rule.snapshots(name, largeFont, prepare = { onNodeWithTag(BrowseTestTags.TAB_CATEGORIES).performClick() }) {
        BrowseScreen(
            topLive = topLiveState(),
            onTopLiveIntent = {},
            categories = state,
            onCategoriesIntent = {},
            onOpenPlayer = {},
            onOpenChannel = {},
            onOpenCategory = {},
        )
    }

    private fun categoryDetail(
        name: String,
        state: CategoryStreamsUiState,
    ) = rule.snapshots(name) {
        CategoryScreen(
            platform = Platform.KICK,
            name = "Cozy Builder",
            state = state,
            onIntent = {},
            onOpenPlayer = {},
            onOpenChannel = {},
        )
    }

    @Test
    fun topLiveMain() = topLive("Browse_TopLive", topLiveState(), largeFont = true)

    @Test
    fun topLivePartialFailure() =
        topLive(
            "Browse_TopLive_PartialFailure_Kick",
            topLiveState(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Unit),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Timeout),
                    ),
            ),
        )

    @Test
    fun topLiveRetryInProgress() =
        topLive(
            "Browse_TopLive_PartialFailure_RetryInProgress",
            topLiveState(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Unit),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Timeout),
                    ),
            ).copy(retrying = setOf(Platform.KICK)),
        )

    @Test
    fun topLiveMoreFailed() =
        topLive(
            "Browse_TopLive_LoadMoreFailed_Merged",
            topLiveState().copy(
                loadMoreErrors = mapOf(Platform.TWITCH to LurkError.Offline, Platform.KICK to LurkError.Offline),
            ),
        )

    @Test
    fun topLiveFailed() =
        topLive(
            "Browse_TopLive_Failed",
            topLiveState(
                streams = emptyList(),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                    ),
            ),
        )

    @Test
    fun topLiveLoading() =
        topLive(
            "Browse_TopLive_Loading",
            topLiveState(
                streams = emptyList(),
                slices = mapOf(Platform.TWITCH to PlatformSlice.Loading, Platform.KICK to PlatformSlice.Loading),
            ),
        )

    @Test
    fun topLiveEmpty() = topLive("Browse_TopLive_Empty", topLiveState(streams = emptyList()))

    @Test
    fun categoriesMain() = categories("Browse_Categories", categoriesState(), largeFont = true)

    @Test
    fun categoriesFailed() =
        categories(
            "Browse_Categories_Failed",
            categoriesState(categories = emptyList(), slice = PlatformSlice.Failed(LurkError.Timeout)),
        )

    @Test
    fun categoriesLoading() =
        categories(
            "Browse_Categories_Loading",
            categoriesState(categories = emptyList(), slice = PlatformSlice.Loading),
        )

    @Test
    fun categoryDetailMain() = categoryDetail("Browse_CategoryDetail", detailState())

    @Test
    fun categoryDetailLoadMoreFailed() =
        categoryDetail("Browse_CategoryDetail_LoadMoreFailed", detailState().copy(loadMoreError = LurkError.Timeout))

    @Test
    fun categoryDetailFailed() =
        categoryDetail(
            "Browse_CategoryDetail_Failed",
            detailState().copy(streams = emptyList(), slice = PlatformSlice.Failed(LurkError.NotFound)),
        )

    private fun topLiveState(
        streams: List<Stream> =
            listOf(
                stream("streamer1", Platform.TWITCH, "Speedrunning all night", 12_400),
                stream("streamer3", Platform.KICK, "Cozy building session", 5_100),
                stream("streamer2", Platform.TWITCH, "Learning to knit, live", 3_200),
            ),
        slices: Map<Platform, PlatformSlice<Unit>> = bothLoaded,
    ) = TopLiveUiState(
        filter = PlatformFilter.ALL,
        streams = streams,
        slices = slices,
        canLoadMore = false,
        loadingMore = false,
    )

    private fun categoriesState(
        categories: List<Category> =
            listOf(
                Category(Platform.TWITCH, "1", "Retro Racers", null, 120_400),
                Category(Platform.TWITCH, "2", "Cozy Builder", null, 48_900),
                Category(Platform.TWITCH, "3", "Just Chatting", null, 31_200),
                Category(Platform.TWITCH, "4", "A category with a very long name that wraps", null, 9_800),
            ),
        slice: PlatformSlice<Unit> = PlatformSlice.Loaded(Unit),
    ) = CategoriesUiState(
        platform = Platform.TWITCH,
        categories = categories,
        slice = slice,
        canLoadMore = false,
    )

    private fun detailState() =
        CategoryStreamsUiState(
            streams =
                listOf(
                    stream("streamer3", Platform.KICK, "Cozy building session", 5_100),
                    stream("streamer4", Platform.KICK, "Late night bricks", 1_900),
                ),
            slice = PlatformSlice.Loaded(Unit),
            canLoadMore = false,
            sorts = listOf(CategorySort.VIEWERS_HIGH, CategorySort.VIEWERS_LOW, CategorySort.RECENT),
            sort = CategorySort.VIEWERS_HIGH,
        )

    private fun stream(
        login: String,
        platform: Platform,
        title: String,
        viewers: Long,
    ) = Stream(
        channel = ChannelRef(platform, login),
        channelId = "1000001",
        displayName = login.replaceFirstChar { it.uppercase() },
        title = title,
        category = Category(platform, "1", "Retro Racers", null),
        viewers = viewers,
        startedAt = now,
        thumbnailUrl = null,
        avatarUrl = null,
    )
}

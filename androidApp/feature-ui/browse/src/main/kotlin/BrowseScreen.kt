package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.CategoryTile
import io.github.kemurixd.lurk.designsystem.component.CollapsibleHeader
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.ErrorState
import io.github.kemurixd.lurk.designsystem.component.PlatformFilterChips
import io.github.kemurixd.lurk.designsystem.component.PlatformLogo
import io.github.kemurixd.lurk.designsystem.component.StreamCard
import io.github.kemurixd.lurk.designsystem.component.StreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.designsystem.component.platformErrors
import io.github.kemurixd.lurk.designsystem.component.rememberCompactHeight
import io.github.kemurixd.lurk.feature.browse.CategoriesIntent
import io.github.kemurixd.lurk.feature.browse.CategoriesUiState
import io.github.kemurixd.lurk.feature.browse.CategoriesViewModel
import io.github.kemurixd.lurk.feature.browse.TopLiveIntent
import io.github.kemurixd.lurk.feature.browse.TopLiveUiState
import io.github.kemurixd.lurk.feature.browse.TopLiveViewModel
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import org.koin.compose.viewmodel.koinViewModel
import io.github.kemurixd.lurk.designsystem.R as DesignR

private const val STREAM_PLACEHOLDERS = 5
private const val CATEGORY_PLACEHOLDERS = 9

/** The grid column width of the category tiles: three across at 412 dp, as in Figma. */
private val CategoryMinWidth = 104.dp

private enum class BrowseTab { TOP_LIVE, CATEGORIES }

/** Gets the Browse view models and hands their state to [BrowseScreen]. */
@Composable
fun BrowseEntry(
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
    topLiveViewModel: TopLiveViewModel = koinViewModel(),
    categoriesViewModel: CategoriesViewModel = koinViewModel(),
) {
    val topLive by topLiveViewModel.state.collectAsStateWithLifecycle()
    val categories by categoriesViewModel.state.collectAsStateWithLifecycle()
    BrowseScreen(
        topLive = topLive,
        onTopLiveIntent = topLiveViewModel::onIntent,
        categories = categories,
        onCategoriesIntent = categoriesViewModel::onIntent,
        onOpenPlayer = onOpenPlayer,
        onOpenChannel = onOpenChannel,
        onOpenCategory = onOpenCategory,
        modifier = modifier,
    )
}

/**
 * Browse: a Top Live / Categories switch over either a merged live grid with platform chips, or a category grid with a
 * Twitch / Kick switch. Top Live is the first tab. Each tab keeps its own scroll position while the other shows.
 */
@Composable
fun BrowseScreen(
    topLive: TopLiveUiState,
    onTopLiveIntent: (TopLiveIntent) -> Unit,
    categories: CategoriesUiState,
    onCategoriesIntent: (CategoriesIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(BrowseTab.TOP_LIVE) }
    val topLiveGrid = rememberLazyGridState()
    val categoriesGrid = rememberLazyGridState()
    // In a landscape phone the switch and chips scroll away with the list, so it gets the whole height.
    CollapsibleHeader(
        collapsible = rememberCompactHeight(),
        modifier = modifier.fillMaxSize(),
        header = {
            Column {
                TabSwitch(tab, onSelect = { tab = it })
                when (tab) {
                    BrowseTab.TOP_LIVE -> {
                        PlatformFilterChips(
                            selected = topLive.filter,
                            onSelect = { onTopLiveIntent(TopLiveIntent.SelectFilter(it)) },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    BrowseTab.CATEGORIES -> {
                        PlatformSwitch(
                            selected = categories.platform,
                            onSelect = { onCategoriesIntent(CategoriesIntent.SelectPlatform(it)) },
                        )
                    }
                }
            }
        },
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                BrowseTab.TOP_LIVE -> {
                    TopLiveGrid(
                        state = topLive,
                        gridState = topLiveGrid,
                        onIntent = onTopLiveIntent,
                        onOpenPlayer = onOpenPlayer,
                        onOpenChannel = onOpenChannel,
                    )
                }

                BrowseTab.CATEGORIES -> {
                    CategoriesGrid(
                        state = categories,
                        gridState = categoriesGrid,
                        onIntent = onCategoriesIntent,
                        onOpenCategory = onOpenCategory,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabSwitch(
    selected: BrowseTab,
    onSelect: (BrowseTab) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val tabs = BrowseTab.entries
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                modifier = Modifier.testTag(tab.tag()).heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (tab == BrowseTab.TOP_LIVE) R.string.tab_top_live else R.string.tab_categories,
                    ),
                )
            }
        }
    }
}

private fun BrowseTab.tag(): String =
    when (this) {
        BrowseTab.TOP_LIVE -> BrowseTestTags.TAB_TOP_LIVE
        BrowseTab.CATEGORIES -> BrowseTestTags.TAB_CATEGORIES
    }

@Composable
private fun PlatformSwitch(
    selected: Platform,
    onSelect: (Platform) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val platforms = Platform.entries
        platforms.forEachIndexed { index, platform ->
            SegmentedButton(
                selected = platform == selected,
                onClick = { onSelect(platform) },
                shape = SegmentedButtonDefaults.itemShape(index, platforms.size),
                modifier =
                    Modifier
                        .testTag(
                            if (platform ==
                                Platform.TWITCH
                            ) {
                                BrowseTestTags.PLATFORM_TWITCH
                            } else {
                                BrowseTestTags.PLATFORM_KICK
                            },
                        ).heightIn(min = 48.dp),
                icon = { PlatformLogo(platform, size = SegmentedButtonDefaults.IconSize) },
            ) {
                Text(stringResource(platform.labelRes))
            }
        }
    }
}

@Composable
private fun TopLiveGrid(
    state: TopLiveUiState,
    gridState: LazyGridState,
    onIntent: (TopLiveIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
) {
    EndOfListEffect(
        state = gridState,
        enabled = state.canLoadMore && !state.loadingMore && state.loadMoreErrors.isEmpty(),
        onLoadMore = { onIntent(TopLiveIntent.LoadMore) },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag(BrowseTestTags.TOP_LIVE_LIST),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        val failures =
            state.slices.mapNotNull { (platform, slice) ->
                (slice as? PlatformSlice.Failed)?.let {
                    platform to
                        it.error
                }
            }
        platformErrors(
            failures = failures,
            nothingElse = failures.size == state.slices.size && state.streams.isEmpty(),
            allFailedTitle = R.string.top_live_error_title,
            retrying = state.retrying,
            onRetry = { onIntent(TopLiveIntent.Retry(it)) },
        )
        val loading = state.slices.values.any { it == PlatformSlice.Loading }
        when {
            state.streams.isNotEmpty() -> {
                items(state.streams, key = { it.key }) { stream ->
                    StreamCard(
                        stream = stream,
                        onOpenPlayer = { onOpenPlayer(stream) },
                        onOpenChannel = { onOpenChannel(stream.channel) },
                        modifier = Modifier.padding(ContentPadding),
                        showPlatform = state.filter == PlatformFilter.ALL,
                    )
                }
            }

            loading -> {
                items(STREAM_PLACEHOLDERS, key = { "placeholder:$it" }) {
                    StreamCardPlaceholder(Modifier.padding(ContentPadding))
                }
            }

            failures.isEmpty() -> {
                fullWidth("empty") {
                    EmptyState(
                        title = stringResource(R.string.top_live_empty_title),
                        body = stringResource(R.string.top_live_empty_body),
                        action = null,
                    )
                }
            }
        }
        pagingFooter(state.loadingMore, state.loadMoreErrors, state.retrying) { onIntent(TopLiveIntent.Retry(it)) }
    }
}

@Composable
private fun CategoriesGrid(
    state: CategoriesUiState,
    gridState: LazyGridState,
    onIntent: (CategoriesIntent) -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    EndOfListEffect(
        state = gridState,
        enabled = state.canLoadMore && !state.loadingMore && state.loadMoreError == null,
        onLoadMore = { onIntent(CategoriesIntent.LoadMore) },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CategoryMinWidth),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag(BrowseTestTags.CATEGORIES_LIST),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        categoryContent(state, onIntent, onOpenCategory)
    }
}

private fun LazyGridScope.categoryContent(
    state: CategoriesUiState,
    onIntent: (CategoriesIntent) -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    val slice = state.slice
    when {
        state.categories.isNotEmpty() -> {
            items(state.categories, key = { "${it.platform.name}:${it.id}" }) { category ->
                // One platform at a time, named by the switch above, so the tiles need no badge.
                CategoryTile(category = category, onClick = { onOpenCategory(category) }, showPlatform = false)
            }
        }

        slice is PlatformSlice.Failed -> {
            fullWidth("failed") {
                ErrorState(
                    title = stringResource(R.string.categories_error_title),
                    error = slice.error,
                    onRetry = { onIntent(CategoriesIntent.Retry) },
                    platform = state.platform,
                    retrying = state.retrying,
                )
            }
        }

        slice == PlatformSlice.NotSignedIn -> {
            // Never shown as empty or loading: say so, with Sign in for this platform.
            fullWidth("signed_out") {
                ErrorState(
                    title = stringResource(R.string.categories_error_title),
                    error = LurkError.NotSignedIn,
                    onRetry = { onIntent(CategoriesIntent.Retry) },
                    platform = state.platform,
                )
            }
        }

        slice == PlatformSlice.Loading -> {
            items(CATEGORY_PLACEHOLDERS, key = { "placeholder:$it" }) { CategoryTilePlaceholder() }
        }

        else -> {
            fullWidth("empty") {
                EmptyState(
                    title = stringResource(R.string.categories_empty_title),
                    body = stringResource(R.string.categories_empty_body),
                    action = null,
                )
            }
        }
    }
    val error = state.loadMoreError
    pagingFooter(
        state.loadingMore,
        if (error == null) emptyMap() else mapOf(state.platform to error),
        if (state.retrying) setOf(state.platform) else emptySet(),
    ) {
        onIntent(CategoriesIntent.Retry)
    }
}

@Composable
private fun CategoryTilePlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 155.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
    )
}

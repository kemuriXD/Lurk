package io.github.kemurixd.lurk.featureui.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.ErrorState
import io.github.kemurixd.lurk.designsystem.component.PlatformBadge
import io.github.kemurixd.lurk.designsystem.component.SectionHeader
import io.github.kemurixd.lurk.designsystem.component.SortMenu
import io.github.kemurixd.lurk.designsystem.component.StreamCard
import io.github.kemurixd.lurk.designsystem.component.StreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsIntent
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsUiState
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsViewModel
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import io.github.kemurixd.lurk.designsystem.R as DesignR

private const val PLACEHOLDERS = 5
private val ArtWidth = 72.dp
private val ArtHeight = 96.dp

/** Gets the [CategoryStreamsViewModel] for one category and hands its state to [CategoryScreen]. */
@Composable
fun CategoryEntry(
    platform: Platform,
    categoryId: String,
    name: String,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryStreamsViewModel = koinViewModel { parametersOf(platform, categoryId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryScreen(
        platform = platform,
        name = name,
        state = state,
        onIntent = viewModel::onIntent,
        onOpenPlayer = onOpenPlayer,
        onOpenChannel = onOpenChannel,
        modifier = modifier,
    )
}

/**
 * One category: its box art, name and platform, then the live channels in it, in the sort picked from the menu at the
 * right of the section header. The top bar and its
 * back arrow belong to the app. A category that is gone offers Go back, through the app's error handlers.
 */
@Composable
fun CategoryScreen(
    platform: Platform,
    name: String,
    state: CategoryStreamsUiState,
    onIntent: (CategoryStreamsIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    EndOfListEffect(
        state = gridState,
        enabled = state.canLoadMore && !state.loadingMore && state.loadMoreError == null,
        onLoadMore = { onIntent(CategoryStreamsIntent.LoadMore) },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        state = gridState,
        modifier = modifier.fillMaxSize().testTag(BrowseTestTags.CATEGORY_LIST),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        fullWidth("header") {
            CategoryHeader(platform, name, boxArtUrl = state.streams.firstNotNullOfOrNull { it.category?.boxArtUrl })
        }
        fullWidth("section") {
            SectionHeader(stringResource(R.string.category_live_channels)) {
                val sort = state.sort
                if (sort != null && state.sorts.size > 1) {
                    SortMenu(
                        selected = sort,
                        options = state.sorts,
                        label = { stringResource(it.labelRes(platform)) },
                        description = { stringResource(R.string.cd_sort_live, it) },
                        onSelect = { onIntent(CategoryStreamsIntent.SelectSort(it)) },
                    )
                }
            }
        }
        streamContent(platform, state, onIntent, onOpenPlayer, onOpenChannel)
        val error = state.loadMoreError
        pagingFooter(state.loadingMore, if (error == null) emptyMap() else mapOf(platform to error)) {
            onIntent(CategoryStreamsIntent.Retry)
        }
    }
}

/** The platform's own name for a sort. Twitch calls its recommendation "Recommended for you". */
@StringRes
private fun CategorySort.labelRes(platform: Platform): Int =
    when (this) {
        CategorySort.FEATURED -> {
            R.string.sort_featured
        }

        CategorySort.RECOMMENDED -> {
            if (platform == Platform.TWITCH) R.string.sort_recommended_for_you else R.string.sort_recommended
        }

        CategorySort.VIEWERS_HIGH -> {
            R.string.sort_viewers_high
        }

        CategorySort.VIEWERS_LOW -> {
            R.string.sort_viewers_low
        }

        CategorySort.RECENT -> {
            R.string.sort_recent
        }
    }

private fun LazyGridScope.streamContent(
    platform: Platform,
    state: CategoryStreamsUiState,
    onIntent: (CategoryStreamsIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
) {
    val slice = state.slice
    when {
        state.streams.isNotEmpty() -> {
            items(state.streams, key = { it.key }) { stream ->
                StreamCard(
                    stream = stream,
                    onOpenPlayer = { onOpenPlayer(stream) },
                    onOpenChannel = { onOpenChannel(stream.channel) },
                    modifier = Modifier.padding(ContentPadding),
                )
            }
        }

        slice is PlatformSlice.Failed -> {
            fullWidth("failed") {
                ErrorState(
                    title = stringResource(R.string.category_error_title),
                    error = slice.error,
                    onRetry = { onIntent(CategoryStreamsIntent.Retry) },
                    platform = platform,
                )
            }
        }

        slice == PlatformSlice.Loading -> {
            items(
                PLACEHOLDERS,
                key = { "placeholder:$it" },
            ) { StreamCardPlaceholder(Modifier.padding(ContentPadding)) }
        }

        else -> {
            fullWidth("empty") {
                EmptyState(
                    title = stringResource(R.string.category_empty_title),
                    body = stringResource(R.string.category_empty_body),
                    action = null,
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    platform: Platform,
    name: String,
    boxArtUrl: String?,
) {
    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ArtWidth, ArtHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = boxArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(platform)
                Text(
                    text =
                        stringResource(platform.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

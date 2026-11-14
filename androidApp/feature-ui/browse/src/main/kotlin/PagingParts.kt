package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorFooter
import io.github.kemurixd.lurk.designsystem.component.groupedByError
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import io.github.kemurixd.lurk.designsystem.R as DesignR

/** How many rows from the end the grid asks for more. */
private const val PRELOAD_ITEMS = 4

internal val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/** Around an error banner or footer in a list. */
internal val BannerPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/**
 * Calls [onLoadMore] when the last visible item is within [PRELOAD_ITEMS] of the end, and again each time the list
 * grows while it is still near the end. Pass [enabled] false while a page loads, after one failed, or when no more
 * exist.
 */
@Composable
internal fun EndOfListEffect(
    state: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val latest by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.filter { (last, total) -> total > 0 && last >= total - 1 - PRELOAD_ITEMS }
            .distinctUntilChanged()
            .collect { latest() }
    }
}

internal fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * One error footer for each distinct failure of a next page, then the spinner while the next page loads. Platforms
 * whose next page failed the same way share one footer, and its Retry asks each. Sign-in errors stay per platform. A
 * platform in [retrying] keeps its footer, with the action off, until its answer lands.
 */
internal fun LazyGridScope.pagingFooter(
    loadingMore: Boolean,
    errors: Map<Platform, LurkError>,
    retrying: Set<Platform> = emptySet(),
    onRetry: (Platform) -> Unit,
) {
    errors.toList().groupedByError().forEach { (platforms, error) ->
        fullWidth("more_error:${platforms.joinToString { it.name }}") {
            ErrorFooter(
                platforms = platforms,
                error = error,
                onRetry = { platforms.forEach(onRetry) },
                modifier = Modifier.padding(BannerPadding),
                retrying = platforms.any { it in retrying },
            )
        }
    }
    if (loadingMore) {
        fullWidth("loading_more") {
            val label = stringResource(DesignR.string.loading)
            Box(
                Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Pull to refresh over a Browse list. Only the pull spinner shows while [refreshing]: the list keeps its rows and any
 * error surface, as Following does. Inside a [io.github.kemurixd.lurk.designsystem.component.CollapsibleHeader] a pull
 * first brings a collapsed header back, then refreshes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pull,
        indicator = {
            if (refreshing || pull.distanceFraction > 0f) {
                PullToRefreshDefaults.Indicator(
                    state = pull,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).testTag(BrowseTestTags.REFRESH_INDICATOR),
                )
            }
        },
        content = content,
    )
}

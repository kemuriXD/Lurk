package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/**
 * What the Top Live list shows.
 *
 * [streams] hold the pages in the order they arrived, each page sorted by viewers. [slices] describe each shown
 * platform's first page; a failed later page leaves its slice loaded and appears in [loadMoreErrors]. A platform in
 * [retrying] is being asked again after a failure: its failure stays in [slices] or [loadMoreErrors] until the
 * answer lands.
 */
data class TopLiveUiState(
    val filter: PlatformFilter,
    val streams: List<Stream>,
    val slices: Map<Platform, PlatformSlice<Unit>>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean,
    val loadMoreErrors: Map<Platform, LurkError> = emptyMap(),
    val retrying: Set<Platform> = emptySet(),
)

sealed interface TopLiveIntent {
    data class SelectFilter(
        val filter: PlatformFilter,
    ) : TopLiveIntent

    /** Fetches the next page of every shown platform that has one. Ignored while anything is loading. */
    data object LoadMore : TopLiveIntent

    /** Starts over from the first pages. */
    data object Refresh : TopLiveIntent

    /** Reloads a platform's failed first page, or its failed next page. */
    data class Retry(
        val platform: Platform,
    ) : TopLiveIntent
}

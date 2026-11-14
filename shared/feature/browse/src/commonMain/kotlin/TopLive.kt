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
 * answer lands. [canLoadMore] is true while some shown platform can fetch its next page now: one whose next page
 * failed waits for its Retry or has reached its end, and doesn't hold the others back.
 */
data class TopLiveUiState(
    val filter: PlatformFilter,
    val streams: List<Stream>,
    val slices: Map<Platform, PlatformSlice<Unit>>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean,
    val loadMoreErrors: Map<Platform, LurkError> = emptyMap(),
    val retrying: Set<Platform> = emptySet(),
    val refreshing: Boolean = false,
)

sealed interface TopLiveIntent {
    data class SelectFilter(
        val filter: PlatformFilter,
    ) : TopLiveIntent

    /** Fetches the next page of every shown platform that has one and no failed page waiting. Ignored while busy. */
    data object LoadMore : TopLiveIntent

    /** Reloads every platform's first page, keeping what is on screen until the answers land. */
    data object Refresh : TopLiveIntent

    /** Reloads a platform's failed first page, or its failed next page. */
    data class Retry(
        val platform: Platform,
    ) : TopLiveIntent
}

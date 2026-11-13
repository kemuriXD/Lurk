package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/**
 * The live streams of one category on one platform. [streams] are pages in arrival order, each in [sort] order.
 * [sorts] are the options to offer, empty when the platform is unavailable, and [sort] is the one in use.
 */
data class CategoryStreamsUiState(
    val streams: List<Stream>,
    val slice: PlatformSlice<Unit>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean = false,
    val loadMoreError: LurkError? = null,
    val sorts: List<CategorySort> = emptyList(),
    val sort: CategorySort? = null,
)

sealed interface CategoryStreamsIntent {
    data object LoadMore : CategoryStreamsIntent

    /** Reloads a failed first page, or a failed next page. */
    data object Retry : CategoryStreamsIntent

    /** Reloads from the first page in [sort]. A sort the platform doesn't offer right now is ignored. */
    data class SelectSort(
        val sort: CategorySort,
    ) : CategoryStreamsIntent
}

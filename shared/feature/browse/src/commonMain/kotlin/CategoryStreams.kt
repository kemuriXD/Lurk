package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/** The live streams of one category on one platform. [streams] are pages in arrival order, each sorted by viewers. */
data class CategoryStreamsUiState(
    val streams: List<Stream>,
    val slice: PlatformSlice<Unit>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean = false,
    val loadMoreError: LurkError? = null,
)

sealed interface CategoryStreamsIntent {
    data object LoadMore : CategoryStreamsIntent

    /** Reloads a failed first page, or a failed next page. */
    data object Retry : CategoryStreamsIntent
}

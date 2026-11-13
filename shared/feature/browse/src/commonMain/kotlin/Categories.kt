package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/** One platform's categories, as chosen by the Twitch/Kick switch. */
data class CategoriesUiState(
    val platform: Platform,
    val categories: List<Category>,
    val slice: PlatformSlice<Unit>,
    val canLoadMore: Boolean,
    val loadingMore: Boolean = false,
    val loadMoreError: LurkError? = null,
)

sealed interface CategoriesIntent {
    data class SelectPlatform(
        val platform: Platform,
    ) : CategoriesIntent

    data object LoadMore : CategoriesIntent

    /** Reloads a failed first page, or a failed next page. */
    data object Retry : CategoriesIntent
}

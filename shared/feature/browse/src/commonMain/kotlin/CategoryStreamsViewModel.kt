package io.github.kemurixd.lurk.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.common.sortedByViewers
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * The live streams of category [categoryId] on [platform]. Each page is sorted by viewers and appended below the ones
 * before it. A blank [categoryId] (Kick leaves some unresolved) is `NotFound` without asking the platform.
 *
 * Call [onIntent] from the main thread.
 */
class CategoryStreamsViewModel(
    platform: Platform,
    categoryId: String,
    platforms: List<StreamingPlatform>,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    private val feed: PagedFeed<Stream>? =
        platforms.firstOrNull { it.id == platform }?.let { source ->
            PagedFeed(source, viewModelScope, dispatchers, Stream::key) { p, cursor ->
                if (categoryId.isBlank()) Outcome.Failure(LurkError.NotFound) else p.liveInCategory(categoryId, cursor)
            }
        }

    private val content: Flow<CategoryStreamsUiState> = feed?.state?.map(::uiState) ?: emptyFlow()

    /** Emits nothing: it starts the first page on the first collector. */
    private val firstLoad: Flow<Nothing> =
        flow {
            feed?.start()
            awaitCancellation()
        }

    val state: StateFlow<CategoryStreamsUiState> =
        merge(content, firstLoad).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = feed?.let { uiState(it.state.value) } ?: UNAVAILABLE,
        )

    /** Handles a user action. */
    fun onIntent(intent: CategoryStreamsIntent) {
        when (intent) {
            CategoryStreamsIntent.LoadMore -> feed?.loadMore()
            CategoryStreamsIntent.Retry -> feed?.retry()
        }
    }

    private fun uiState(feed: PagedState<Stream>) =
        CategoryStreamsUiState(
            streams = feed.pages.flatMap { it.sortedByViewers() },
            slice = feed.slice,
            canLoadMore = feed.canLoadMore,
            loadingMore = feed.loadingMore,
            loadMoreError = feed.moreError,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        val UNAVAILABLE =
            CategoryStreamsUiState(emptyList(), PlatformSlice.Failed(LurkError.Unsupported), canLoadMore = false)
    }
}

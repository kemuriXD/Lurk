package io.github.kemurixd.lurk.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * The category list of one platform at a time, [Platform.TWITCH] first. Each platform loads when it is first chosen
 * and keeps its pages while the other one is shown. Paging works as in [TopLiveViewModel].
 *
 * Call [onIntent] from the main thread.
 */
class CategoriesViewModel(
    platforms: List<StreamingPlatform>,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    private val feeds: Map<Platform, PagedFeed<Category>> =
        platforms.associate { platform ->
            platform.id to
                PagedFeed(platform, viewModelScope, dispatchers, { it.feedKey }) { source, cursor ->
                    source.categories(cursor)
                }
        }

    private val selected = MutableStateFlow(Platform.TWITCH)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<CategoriesUiState> =
        selected.flatMapLatest { platform ->
            feeds[platform]?.state?.map { uiState(platform, it) } ?: flowOf(unavailable(platform))
        }

    /** Emits nothing: it starts the chosen platform's first page on the first collector. */
    private val firstLoad: Flow<Nothing> =
        flow {
            feeds[selected.value]?.start()
            awaitCancellation()
        }

    val state: StateFlow<CategoriesUiState> =
        merge(content, firstLoad).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = initial(),
        )

    /** Handles a user action. */
    fun onIntent(intent: CategoriesIntent) {
        when (intent) {
            is CategoriesIntent.SelectPlatform -> {
                selected.value = intent.platform
                feeds[intent.platform]?.start()
            }

            CategoriesIntent.LoadMore -> {
                feeds[selected.value]?.loadMore()
            }

            CategoriesIntent.Retry -> {
                feeds[selected.value]?.retry()
            }
        }
    }

    private fun initial(): CategoriesUiState {
        val platform = selected.value
        return feeds[platform]?.let { uiState(platform, it.state.value) } ?: unavailable(platform)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        val Category.feedKey: String get() = "${platform.name}:$id"

        fun uiState(
            platform: Platform,
            feed: PagedState<Category>,
        ) = CategoriesUiState(
            platform = platform,
            categories = feed.pages.flatten(),
            slice = feed.slice,
            canLoadMore = feed.canLoadMore,
            loadingMore = feed.loadingMore,
            loadMoreError = feed.moreError,
        )

        fun unavailable(platform: Platform) =
            CategoriesUiState(platform, emptyList(), PlatformSlice.Failed(LurkError.Unsupported), canLoadMore = false)
    }
}

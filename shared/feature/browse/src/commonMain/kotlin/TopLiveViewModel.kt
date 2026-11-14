package io.github.kemurixd.lurk.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.common.sortedByViewers
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * The Top Live list: every platform's live streams, merged by viewers.
 *
 * The first page of each platform loads when [state] first has a collector. [TopLiveIntent.LoadMore] fetches the next
 * page of every shown platform that still has a cursor, sorts that batch and appends it below what is already there,
 * so nothing on screen moves. The platform filter only hides rows; it never reloads. Five seconds after the last
 * collector leaves the cached state resets, while the loaded pages stay.
 *
 * Call [onIntent] from the main thread.
 */
class TopLiveViewModel(
    platforms: List<StreamingPlatform>,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    private val feeds: Map<Platform, PagedFeed<Stream>> =
        platforms.associate { platform ->
            platform.id to
                PagedFeed(
                    platform,
                    viewModelScope,
                    dispatchers,
                    Stream::key,
                ) { source, cursor -> source.topLive(cursor) }
        }

    /** The next batch number. Every load that starts gets a new one, so its rows land below all earlier batches. */
    private var nextBatch = 0

    private val filter = MutableStateFlow(PlatformFilter.ALL)

    private val content: Flow<TopLiveUiState> =
        combine(filter, combine(feeds.values.map { it.state }) { it.toList() }) { selected, states ->
            uiState(selected, feeds.keys.zip(states).toMap())
        }

    /** Emits nothing: it starts the first page of each platform on the first collector. */
    private val firstLoad: Flow<Nothing> =
        flow {
            val batch = nextBatch++
            feeds.values.forEach { it.start(batch) }
            awaitCancellation()
        }

    val state: StateFlow<TopLiveUiState> =
        merge(content, firstLoad).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = uiState(PlatformFilter.ALL, feeds.mapValues { it.value.state.value }),
        )

    /** Handles a user action. */
    fun onIntent(intent: TopLiveIntent) {
        when (intent) {
            is TopLiveIntent.SelectFilter -> {
                filter.value = intent.filter
            }

            TopLiveIntent.LoadMore -> {
                loadMore()
            }

            TopLiveIntent.Refresh -> {
                val batch = nextBatch++
                feeds.values.forEach { it.refresh(batch) }
            }

            is TopLiveIntent.Retry -> {
                feeds[intent.platform]?.retry(nextBatch++)
            }
        }
    }

    /** One load-more for all shown platforms together; while any of them is busy nothing starts. */
    private fun loadMore() {
        val shown = feeds.filterKeys(filter.value::includes).values
        if (shown.none { it.busy }) {
            val batch = nextBatch++
            shown.forEach { it.loadMore(batch) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun uiState(
            selected: PlatformFilter,
            states: Map<Platform, PagedState<Stream>>,
        ): TopLiveUiState {
            val shown = states.filterKeys(selected::includes)
            val streams =
                shown.values
                    .flatMap { it.pages }
                    .groupBy { it.id }
                    .toSortedMap()
                    .values
                    .flatMap { batch -> batch.flatMap { it.items }.sortedByViewers() }
            val moreErrors: Map<Platform, LurkError> =
                shown.mapNotNull { (platform, s) -> s.moreError?.let { platform to it } }.toMap()
            return TopLiveUiState(
                filter = selected,
                streams = streams,
                slices = shown.mapValues { it.value.slice },
                canLoadMore = shown.values.any { it.canLoadMore },
                loadingMore = shown.values.any { it.loadingMore },
                loadMoreErrors = moreErrors,
                retrying = shown.filterValues { it.retrying }.keys,
            )
        }
    }
}

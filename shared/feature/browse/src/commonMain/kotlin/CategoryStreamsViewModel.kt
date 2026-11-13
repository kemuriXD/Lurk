package io.github.kemurixd.lurk.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.common.sortedByViewers
import io.github.kemurixd.lurk.platform.api.Capabilities
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/** The sorts on offer and the one in use. */
private data class SortChoice(
    val options: List<CategorySort>,
    val sort: CategorySort,
)

/** A pick the user can't use (another platform's sort, or one that needs sign-in) gives the platform default. */
private fun Capabilities.choiceOf(
    pick: CategorySort?,
    signedIn: Boolean,
): SortChoice {
    val options = categorySortOptions(signedIn)
    return SortChoice(options, pick?.takeIf { it in options } ?: defaultCategorySort(signedIn))
}

/**
 * The live streams of category [categoryId] on [platform], in the sort the user picked. Viewer sorts order each page
 * before it is appended below the ones before; other sorts keep the platform's order. A blank [categoryId] (Kick
 * leaves some unresolved) is `NotFound` without asking the platform.
 *
 * The sort options and the starting sort come from the platform's capabilities and its sign-in state. A pick lasts
 * as long as this view model and is not stored. If signing out takes the picked sort away, the list falls back to the
 * platform default.
 *
 * Call [onIntent] from the main thread.
 */
class CategoryStreamsViewModel(
    platform: Platform,
    categoryId: String,
    platforms: List<StreamingPlatform>,
    accounts: AccountsState,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    private val source: StreamingPlatform? = platforms.firstOrNull { it.id == platform }

    /** The user's pick, or null for the platform default. */
    private val picked = MutableStateFlow<CategorySort?>(null)

    private val choices: Flow<SortChoice> =
        if (source == null) {
            emptyFlow()
        } else {
            combine(picked, accounts.states.map { it[platform] != AccountState.SignedOut }) { pick, signedIn ->
                source.capabilities.choiceOf(pick, signedIn)
            }.distinctUntilChanged()
        }

    private val firstChoice: SortChoice? = source?.capabilities?.choiceOf(null, accounts.signedIn(platform))

    /** The sort the feed loads with. It changes only together with a refresh of the feed. */
    private var activeSort: CategorySort? = firstChoice?.sort

    private val feed: PagedFeed<Stream>? =
        source?.let {
            PagedFeed(it, viewModelScope, dispatchers, Stream::key) { p, cursor ->
                val sort = activeSort
                when {
                    categoryId.isBlank() -> Outcome.Failure(LurkError.NotFound)
                    sort == null -> Outcome.Failure(LurkError.Unsupported)
                    else -> p.liveInCategory(categoryId, cursor, sort)
                }
            }
        }

    private val content: Flow<CategoryStreamsUiState> =
        feed?.let { combine(it.state, choices, ::uiState) } ?: emptyFlow()

    /**
     * Emits nothing. On the first collector it starts the first page. From then on, whenever the sort in use changes,
     * it reloads from the first page and drops any page still on its way.
     */
    private val loads: Flow<Nothing> =
        flow {
            choices.collect { choice ->
                if (choice.sort == activeSort) {
                    feed?.start()
                } else {
                    activeSort = choice.sort
                    feed?.refresh()
                }
            }
        }

    val state: StateFlow<CategoryStreamsUiState> =
        merge(content, loads).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue =
                if (feed != null && firstChoice != null) uiState(feed.state.value, firstChoice) else UNAVAILABLE,
        )

    /** Handles a user action. */
    fun onIntent(intent: CategoryStreamsIntent) {
        when (intent) {
            CategoryStreamsIntent.LoadMore -> feed?.loadMore()
            CategoryStreamsIntent.Retry -> feed?.retry()
            is CategoryStreamsIntent.SelectSort -> picked.value = intent.sort
        }
    }

    private fun uiState(
        feed: PagedState<Stream>,
        choice: SortChoice,
    ) = CategoryStreamsUiState(
        streams = feed.pages.flatMap { ordered(it.items, choice.sort) },
        slice = feed.slice,
        canLoadMore = feed.canLoadMore,
        loadingMore = feed.loadingMore,
        loadMoreError = feed.moreError,
        sorts = choice.options,
        sort = choice.sort,
    )

    private fun ordered(
        page: List<Stream>,
        sort: CategorySort,
    ): List<Stream> =
        when (sort) {
            CategorySort.VIEWERS_HIGH -> page.sortedByViewers()
            CategorySort.VIEWERS_LOW -> page.sortedBy { it.viewers }
            else -> page
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        val UNAVAILABLE =
            CategoryStreamsUiState(emptyList(), PlatformSlice.Failed(LurkError.Unsupported), canLoadMore = false)
    }
}

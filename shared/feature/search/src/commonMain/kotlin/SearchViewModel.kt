package io.github.kemurixd.lurk.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.loadBoth
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cross-platform search: one `search` call per platform, run at once, merged into one list.
 *
 * A query change waits [debounce], then searches. A newer query cancels the older one, request and all, so a slow
 * answer for "a" can never land over the results for "ab". The query is trimmed; a blank one clears the results and
 * sends nothing. Platforms answer on their own: the first to finish shows while the other still reads `Loading`, and
 * a failed platform never hides the other's results. The previous results stay on screen until their replacements
 * arrive. The platform filter only hides rows; it never searches again.
 *
 * The search pipeline runs while [state] has a collector. Five seconds after the last one leaves it stops and the
 * cached state resets; the results stay, and a collector that comes back searches again only if the last search did
 * not finish.
 *
 * Call [onIntent] from the main thread.
 */
class SearchViewModel(
    private val platforms: List<StreamingPlatform>,
    private val dispatchers: DispatcherProvider,
    private val debounce: Duration = 300.milliseconds,
) : ViewModel() {
    /** Everything the screen shows, in one value so it never sees the query and its results out of step. */
    private val core = MutableStateFlow(Core())

    /** Running retries, by platform. Touched only on the main thread. */
    private val retries = mutableMapOf<Platform, Job>()

    /** Emits nothing: it searches whenever the trimmed query changes. */
    private val pipeline: Flow<Nothing> =
        flow {
            core.map { it.query.trim() }.distinctUntilChanged().collectLatest(::search)
        }

    val state: StateFlow<SearchUiState> =
        merge(core.map(::uiState), pipeline).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = uiState(core.value),
        )

    /** Handles a user action. */
    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> changeQuery(intent.text)
            SearchIntent.Clear -> changeQuery("")
            is SearchIntent.SelectFilter -> core.update { it.copy(filter = intent.filter) }
            is SearchIntent.Retry -> retry(intent.platform)
        }
    }

    private fun changeQuery(text: String) {
        core.update { now ->
            when {
                text.trim() == now.query.trim() -> now.copy(query = text)
                text.isBlank() -> now.copy(query = text, found = emptyMap(), status = emptyMap(), searched = "")
                else -> now.copy(query = text, status = platforms.associate { it.id to PlatformSlice.Loading })
            }
        }
    }

    /** Waits out the debounce, then searches every platform. Cancelled by the next query. */
    private suspend fun search(query: String) {
        retries.values.forEach(Job::cancel)
        retries.clear()
        val now = core.value
        // A collector coming back to a finished search for this query has nothing to redo.
        val finished = now.searched == query && now.status.values.none { it == PlatformSlice.Loading }
        if (query.isEmpty() || finished) return
        delay(debounce)
        core.update { it.copy(searched = query) }
        coroutineScope {
            for (platform in platforms) launch { load(platform, query) }
        }
    }

    private fun retry(platform: Platform) {
        val target = platforms.firstOrNull { it.id == platform } ?: return
        val query = core.value.query.trim()
        if (query.isEmpty()) return
        retries.remove(platform)?.cancel()
        core.update { it.copy(status = it.status + (platform to PlatformSlice.Loading)) }
        retries[platform] = viewModelScope.launch { load(target, query) }
    }

    private suspend fun load(
        platform: StreamingPlatform,
        query: String,
    ) {
        val slice =
            withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.search(query) } }.getValue(platform.id)
        // The query may have moved on while a retry ran, so only the current one's answer is kept.
        core.update { now ->
            if (now.query.trim() != query) {
                now
            } else {
                val answer = (slice as? PlatformSlice.Loaded)?.value
                now.copy(
                    found = if (answer != null) now.found + (platform.id to answer) else now.found - platform.id,
                    status = now.status + (platform.id to slice.asStatus()),
                )
            }
        }
    }

    private fun PlatformSlice<SearchResults>.asStatus(): PlatformSlice<Unit> =
        when (this) {
            PlatformSlice.Loading -> PlatformSlice.Loading

            // Search needs no account. A platform that says otherwise simply has nothing to show.
            PlatformSlice.NotSignedIn -> PlatformSlice.Loaded(Unit)

            is PlatformSlice.Loaded -> PlatformSlice.Loaded(Unit)

            is PlatformSlice.Failed -> this
        }

    /**
     * [found] holds each platform's latest answer and [status] where each platform stands. [searched] is the trimmed
     * query the last search was started for.
     */
    private data class Core(
        val query: String = "",
        val filter: PlatformFilter = PlatformFilter.ALL,
        val found: Map<Platform, SearchResults> = emptyMap(),
        val status: Map<Platform, PlatformSlice<Unit>> = emptyMap(),
        val searched: String = "",
    )

    private fun uiState(core: Core): SearchUiState {
        val shown = platforms.map { it.id }.sortedBy { it.ordinal }.filter(core.filter::includes)
        val slices = shown.mapNotNull { id -> core.status[id]?.let { id to it } }.toMap()
        val channels: List<Channel> = shown.flatMap { core.found[it]?.channels.orEmpty() }.sortedBy { it.live == null }
        val categories: List<Category> = shown.flatMap { core.found[it]?.categories.orEmpty() }
        val phase =
            when {
                core.query.isBlank() -> {
                    SearchPhase.EMPTY
                }

                slices.values.any { it == PlatformSlice.Loading } -> {
                    SearchPhase.TYPING
                }

                channels.isNotEmpty() || categories.isNotEmpty() ||
                    slices.values.any {
                        it is PlatformSlice.Failed
                    }
                -> {
                    SearchPhase.RESULTS
                }

                else -> {
                    SearchPhase.NO_RESULTS
                }
            }
        return SearchUiState(core.query, core.filter, channels, categories, slices, phase)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

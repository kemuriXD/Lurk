package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.isRetryable
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.loadBoth
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The items of one fetched page, tagged with the load [id] that asked for it. */
internal data class Batch<T>(
    val id: Int,
    val items: List<T>,
)

/**
 * Everything one platform has loaded for a paged list. [pages] keeps each fetched page apart, tagged with the batch
 * it belongs to, so a list that merges several platforms can sort a batch without touching the batches above it.
 * The cursor sits here, not in the slice (`PlatformSlice.Loaded` carries no cursor), and a load-more failure sits in
 * [moreError] while [slice] stays loaded. [retrying] is true while a retry of a failed first page or a failed next page
 * runs: the failure stays on screen (no skeleton, no spinner row) until the answer lands.
 */
internal data class PagedState<T>(
    val pages: List<Batch<T>> = emptyList(),
    val slice: PlatformSlice<Unit> = PlatformSlice.Loading,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreError: LurkError? = null,
    val retrying: Boolean = false,
) {
    val canLoadMore: Boolean get() = slice is PlatformSlice.Loaded && cursor != null

    /** Whether a plain load-more may ask for the next page: there is one, and no failed page waits for its Retry. */
    val canPageOn: Boolean get() = canLoadMore && moreError == null
}

/**
 * One platform's paged list. It loads the first page, then the page after [PagedState.cursor] on request, each in its
 * own job. A result is shown only if its job is still the registered one, so a refresh drops loads it replaced.
 * Pages repeating an item (by [keyOf]) are shown once. Call from the main thread.
 */
internal class PagedFeed<T>(
    private val platform: StreamingPlatform,
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val keyOf: (T) -> String,
    private val fetch: suspend (StreamingPlatform, String?) -> Outcome<Page<T>>,
) {
    private val mutableState = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = mutableState.asStateFlow()

    private var running: Job? = null

    /** True while any load of this feed is running. */
    val busy: Boolean get() = running?.isActive == true

    /** Loads the first page unless it already has been, or is on its way. */
    fun start(batch: Int = 0) {
        if (running == null && mutableState.value.slice == PlatformSlice.Loading) {
            launch(cursor = null, first = true, batch = batch)
        }
    }

    /** Fetches the page after the cursor. Does nothing while a load runs or when there is no next page. */
    fun loadMore(batch: Int = 0) {
        val current = mutableState.value
        if (running == null && current.canLoadMore) launch(current.cursor, first = false, batch = batch)
    }

    /** Reloads a failed first page, or tries a failed load-more again, keeping the failure on screen meanwhile. */
    fun retry(batch: Int = 0) {
        if (running != null) return
        val current = mutableState.value
        when {
            current.slice is PlatformSlice.Failed -> {
                launch(cursor = null, first = true, batch = batch, retry = true)
            }

            current.moreError != null && current.canLoadMore -> {
                launch(current.cursor, first = false, batch = batch, retry = true)
            }
        }
    }

    /** Drops whatever is loaded or loading and starts again from the first page. */
    fun refresh(batch: Int = 0) {
        running?.cancel()
        running = null
        mutableState.value = PagedState()
        launch(cursor = null, first = true, batch = batch)
    }

    private fun launch(
        cursor: String?,
        first: Boolean,
        batch: Int,
        retry: Boolean = false,
    ) {
        mutableState.update {
            when {
                retry -> it.copy(retrying = true)
                first -> PagedState(slice = PlatformSlice.Loading)
                else -> it.copy(loadingMore = true, moreError = null)
            }
        }
        // Lazy, so the job is registered before it runs and can recognise itself in [running].
        val job = scope.launch(start = CoroutineStart.LAZY) { load(cursor, first, batch) }
        running = job
        job.start()
    }

    private suspend fun load(
        cursor: String?,
        first: Boolean,
        batch: Int,
    ) {
        val job = currentCoroutineContext().job
        try {
            val result =
                withContext(dispatchers.io) { loadBoth(listOf(platform)) { fetch(it, cursor) } }.getValue(platform.id)
            if (running === job) mutableState.update { settled(it, result, first, batch) }
        } finally {
            if (running === job) {
                running = null
                mutableState.update { it.copy(loadingMore = false, retrying = false) }
            }
        }
    }

    private fun settled(
        before: PagedState<T>,
        result: PlatformSlice<Page<T>>,
        first: Boolean,
        batch: Int,
    ): PagedState<T> =
        when (result) {
            is PlatformSlice.Loaded -> {
                val seen = before.pages.flatMap { it.items }.mapTo(HashSet(), keyOf)
                val fresh = result.value.items.filter { seen.add(keyOf(it)) }
                before.copy(
                    pages = if (first) listOf(Batch(batch, fresh)) else before.pages + Batch(batch, fresh),
                    slice = PlatformSlice.Loaded(Unit),
                    cursor = result.value.nextCursor,
                    moreError = null,
                )
            }

            is PlatformSlice.Failed -> {
                when {
                    first -> before.copy(slice = result)

                    // Asking for the same page again would fail the same way, so end the list here.
                    !result.error.isRetryable -> before.copy(moreError = result.error, cursor = null)

                    else -> before.copy(moreError = result.error)
                }
            }

            PlatformSlice.NotSignedIn -> {
                if (first) {
                    before.copy(slice = PlatformSlice.NotSignedIn)
                } else {
                    before.copy(moreError = LurkError.NotSignedIn, cursor = null)
                }
            }

            PlatformSlice.Loading -> {
                before
            }
        }
}

package io.github.kemurixd.lurk.feature.search

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/** Which of the Figma states the Search screen is in. */
enum class SearchPhase {
    /** The query is blank: nothing was searched. */
    EMPTY,

    /** A search is waiting out the debounce or running on at least one shown platform. */
    TYPING,

    /** At least one shown platform answered with something, or failed. */
    RESULTS,

    /** Every shown platform answered and found nothing. */
    NO_RESULTS,
}

/**
 * The Search screen. [query] is the text as typed; the search itself uses it trimmed. [channels] list live ones first,
 * then each platform's own order. [channels], [categories] and [slices] cover only the platforms [filter] includes.
 *
 * While a new search runs, the previous results stay on screen and the slices read `Loading`.
 */
data class SearchUiState(
    val query: String = "",
    val filter: PlatformFilter = PlatformFilter.ALL,
    val channels: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val slices: Map<Platform, PlatformSlice<Unit>> = emptyMap(),
    val phase: SearchPhase = SearchPhase.EMPTY,
)

/** What the Search screen can ask of [SearchViewModel]. */
sealed interface SearchIntent {
    /** The text field changed. Searches after a pause; a blank [text] clears without a request. */
    data class QueryChanged(
        val text: String,
    ) : SearchIntent

    /** Picks a platform chip. Hides rows; never searches again. */
    data class SelectFilter(
        val filter: PlatformFilter,
    ) : SearchIntent

    /** Searches one platform again, from its error chip. */
    data class Retry(
        val platform: Platform,
    ) : SearchIntent

    /**
     * An offline result came on screen. If its platform's search leaves out when it was last live, that is looked up
     * in the background and the row gains the time when it lands.
     */
    data class OfflineRowShown(
        val channel: ChannelRef,
    ) : SearchIntent

    /** That row left the screen. A lookup for it still waiting its turn is dropped. Pairs with [OfflineRowShown]. */
    data class OfflineRowHidden(
        val channel: ChannelRef,
    ) : SearchIntent

    /** Empties the field and the results. */
    data object Clear : SearchIntent
}

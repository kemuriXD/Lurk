package io.github.kemurixd.lurk.feature.following

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/**
 * The Following screen. [live] and [offline] are already filtered and sorted; [slices] and [refreshErrors] cover only
 * the platforms [filter] includes.
 *
 * [refreshing] is true while any platform loads. [manualRefresh] is true only while a reload the user asked for
 * (pull-to-refresh) runs, and is the only one that may show a spinner: the timed reload and the one after coming back
 * run quietly under the items.
 *
 * A refresh that fails over loaded items keeps them as `Loaded(stale = true)` and puts the error in [refreshErrors].
 * A first load that fails is a `Failed` slice instead.
 */
data class FollowingUiState(
    val filter: PlatformFilter,
    val sort: FollowingSort,
    val live: List<Stream>,
    val offline: List<Channel>,
    val slices: Map<Platform, PlatformSlice<Followed>>,
    val refreshing: Boolean,
    val manualRefresh: Boolean = false,
    val refreshErrors: Map<Platform, LurkError> = emptyMap(),
)

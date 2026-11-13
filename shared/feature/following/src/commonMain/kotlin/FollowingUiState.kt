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
    val refreshErrors: Map<Platform, LurkError> = emptyMap(),
)

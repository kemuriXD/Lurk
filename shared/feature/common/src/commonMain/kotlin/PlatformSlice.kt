package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.common.LurkError

/** One platform's half of a two-platform list. One half failing never blocks the other. */
sealed interface PlatformSlice<out T> {
    data object Loading : PlatformSlice<Nothing>

    /** The platform needs a signed-in account for this list and has none. */
    data object NotSignedIn : PlatformSlice<Nothing>

    /** [stale] is true while a refresh runs over the shown value, or after one failed. */
    data class Loaded<T>(
        val value: T,
        val stale: Boolean = false,
    ) : PlatformSlice<T>

    data class Failed(
        val error: LurkError,
    ) : PlatformSlice<Nothing>
}

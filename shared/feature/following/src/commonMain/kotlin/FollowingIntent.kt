package io.github.kemurixd.lurk.feature.following

import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.FollowingSort
import io.github.kemurixd.lurk.feature.common.PlatformFilter

/** What the Following screen can ask of [FollowingViewModel]. */
sealed interface FollowingIntent {
    /** Picks a platform chip. Remembered across launches. */
    data class SelectFilter(
        val filter: PlatformFilter,
    ) : FollowingIntent

    /** Picks the live sort. Remembered across launches. */
    data class SelectSort(
        val sort: FollowingSort,
    ) : FollowingIntent

    /** Reloads every platform; sent on resume and on pull-to-refresh. A platform already loading is left alone. */
    data object Refresh : FollowingIntent

    /** Reloads one platform, from its error chip. */
    data class Retry(
        val platform: Platform,
    ) : FollowingIntent
}

package io.github.kemurixd.lurk.core.storage

import kotlinx.serialization.Serializable

/**
 * User settings. Crash reports stay off until the user opts in. Every field has a default, so JSON stored by an older
 * build still loads.
 */
@Serializable
data class AppSettings(
    val crashReportsOptIn: Boolean = false,
    val onboardingComplete: Boolean = false,
    val followingFilter: PlatformFilterSetting = PlatformFilterSetting.ALL,
    val followingSort: FollowingSortSetting = FollowingSortSetting.VIEWERS,
    val followingOfflineCollapsed: Boolean = false,
)

/** The stored platform chip. Mirrors the feature layer's `PlatformFilter`, which core can't see. */
@Serializable
enum class PlatformFilterSetting {
    ALL,
    TWITCH,
    KICK,
}

/** The stored Following sort. Mirrors the feature layer's `FollowingSort`, which core can't see. */
@Serializable
enum class FollowingSortSetting {
    VIEWERS,
    RECENT,
    NAME,
}

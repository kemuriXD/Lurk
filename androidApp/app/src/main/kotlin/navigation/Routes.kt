package io.github.kemurixd.lurk.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.serialization.Serializable

// Navigation 3 keys. Each one is saved with the back stack, so every field must be serializable.

@Serializable
data object FollowingRoute : NavKey

@Serializable
data object BrowseRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data class CategoryRoute(
    val platform: Platform,
    val id: String,
    val name: String,
) : NavKey

@Serializable
data class ChannelRoute(
    val platform: Platform,
    val login: String,
) : NavKey

/** A stream tap lands here. P7b swaps the placeholder screen for the player behind this same key. */
@Serializable
data class PlayerRoute(
    val platform: Platform,
    val login: String,
    val title: String,
    val thumbnailUrl: String?,
) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class LoginRoute(
    val platform: Platform,
) : NavKey

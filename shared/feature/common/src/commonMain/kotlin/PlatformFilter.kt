package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.Platform

/** The platform chips above a list: everything, or one platform. */
enum class PlatformFilter {
    ALL,
    TWITCH,
    KICK,
    ;

    fun includes(platform: Platform): Boolean =
        when (this) {
            ALL -> true
            TWITCH -> platform == Platform.TWITCH
            KICK -> platform == Platform.KICK
        }
}

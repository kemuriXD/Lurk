package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** A streaming platform Lurk can watch. */
@Serializable
enum class Platform(
    val id: String,
) {
    TWITCH("twitch"),
    KICK("kick"),
    ;

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}

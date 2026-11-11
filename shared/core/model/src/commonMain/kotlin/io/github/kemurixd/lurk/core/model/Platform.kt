package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A streaming platform Lurk can watch. */
@Serializable
enum class Platform(
    val id: String,
) {
    @SerialName("twitch")
    TWITCH("twitch"),

    @SerialName("kick")
    KICK("kick"),
    ;

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}

package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** Identifies a channel on one platform by its login. */
@Serializable
data class ChannelRef(
    val platform: Platform,
    val login: String,
) {
    init {
        require(login.isNotBlank()) { "login must not be blank" }
        require(login.none { it.isWhitespace() }) { "login must not contain whitespace" }
    }

    companion object {
        /** Normalises user input: trims and lowercases the login. */
        fun of(
            platform: Platform,
            login: String,
        ): ChannelRef = ChannelRef(platform, login.trim().lowercase())
    }
}

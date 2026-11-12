package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.model.Platform

/** Where a platform's web login lives and which cookie carries the session token afterwards. */
data class LoginSpec(
    val platform: Platform,
    val loginUrl: String,
    val cookieDomain: String,
    val cookieName: String,
) {
    /** Turns the raw cookie value into the token the API expects. Kick stores it URL-encoded. */
    fun tokenFrom(cookieValue: String): String =
        when (platform) {
            Platform.KICK -> percentDecode(cookieValue)
            Platform.TWITCH -> cookieValue
        }

    companion object {
        val TWITCH = LoginSpec(Platform.TWITCH, "https://www.twitch.tv/login", ".twitch.tv", "auth-token")
        val KICK = LoginSpec(Platform.KICK, "https://kick.com/login", "kick.com", "session_token")

        fun forPlatform(platform: Platform): LoginSpec =
            when (platform) {
                Platform.TWITCH -> TWITCH
                Platform.KICK -> KICK
            }
    }
}

private const val HEX_RADIX = 16
private const val ESCAPE_LENGTH = 3

/** Decodes `%XX` escapes as UTF-8. `+` stays as is (cookies are not form data) and broken escapes are kept. */
internal fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val out = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val byte = value.escapedByteAt(i)
        if (byte != null) {
            out += byte
            i += ESCAPE_LENGTH
        } else {
            val next = value.indexOf('%', startIndex = i + 1).let { if (it < 0) value.length else it }
            value.substring(i, next).encodeToByteArray().forEach { out += it }
            i = next
        }
    }
    return out.toByteArray().decodeToString()
}

private fun String.escapedByteAt(index: Int): Byte? =
    if (this[index] == '%' && index + ESCAPE_LENGTH <= length) {
        substring(index + 1, index + ESCAPE_LENGTH)
            .takeIf { hex -> hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } }
            ?.toInt(HEX_RADIX)
            ?.toByte()
    } else {
        null
    }

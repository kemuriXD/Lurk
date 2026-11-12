package io.github.kemurixd.lurk.platform.twitch.playback

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlin.random.Random

/** Exclusive bound of the `p` cache buster the web player adds. */
private const val CACHE_BUSTER_BOUND = 10_000_000

/** The master playlist URL for [login] on usher, signed with [token] and asking for the web player's renditions. */
internal fun usherUrl(
    usherBase: String,
    login: String,
    token: PlaybackToken,
    random: Random,
): String =
    URLBuilder(usherBase)
        .apply {
            appendPathSegments("api", "channel", "hls", "$login.m3u8")
            parameters.append("sig", token.signature)
            parameters.append("token", token.value)
            parameters.append("allow_source", "true")
            parameters.append("allow_audio_only", "true")
            parameters.append("fast_bread", "true")
            parameters.append("playlist_include_framerate", "true")
            parameters.append("player_backend", "mediaplayer")
            parameters.append("p", random.nextInt(CACHE_BUSTER_BOUND).toString())
        }.buildString()

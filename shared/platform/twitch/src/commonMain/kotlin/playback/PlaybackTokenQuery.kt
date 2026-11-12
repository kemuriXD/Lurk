package io.github.kemurixd.lurk.platform.twitch.playback

import io.github.kemurixd.lurk.playback.TokenFlags
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal const val PLAYBACK_TOKEN_OPERATION = "PlaybackAccessToken_Template"

/** The live branch of the web player's token query, asking for a `site` player on `web`. */
internal const val PLAYBACK_TOKEN_QUERY =
    "query PlaybackAccessToken_Template(\$login: String!) { " +
        "streamPlaybackAccessToken(channelName: \$login, " +
        "params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}) " +
        "{ value signature } }"

/** A signed stream token and the ad flags read from it. [toString] leaves out the value and the signature. */
internal class PlaybackToken(
    val value: String,
    val signature: String,
    val flags: TokenFlags,
) {
    override fun toString(): String = "PlaybackToken(flags=$flags)"
}

internal fun playbackTokenVariables(login: String): JsonObject = buildJsonObject { put("login", login) }

/** Reads `streamPlaybackAccessToken` from the `data` object. Null means Twitch has no channel with that login. */
internal fun decodePlaybackToken(data: JsonObject): PlaybackToken? {
    val token = data.getValue("streamPlaybackAccessToken")
    if (token is JsonNull) return null
    val fields = token.jsonObject
    val value = fields.string("value")
    return PlaybackToken(value, fields.string("signature"), tokenFlagsOf(value))
}

/**
 * The ad flags inside a token value, which is itself JSON. A missing or unreadable `subscriber` or `turbo` reads
 * as false and a missing `show_ads` as true, so a token that cannot be read never claims to be ad-free.
 */
internal fun tokenFlagsOf(value: String): TokenFlags {
    val fields =
        try {
            Json.parseToJsonElement(value) as? JsonObject
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            null
        }
    return TokenFlags(
        subscriber = fields.flag("subscriber") ?: false,
        turbo = fields.flag("turbo") ?: false,
        showAds = fields.flag("show_ads") ?: true,
    )
}

private fun JsonObject?.flag(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.string(key: String): String {
    val field = get(key) as? JsonPrimitive
    return checkNotNull(field?.takeIf { it.isString }?.content) { "playback token has no $key" }
}

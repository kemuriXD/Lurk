package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.provider.BttvParser
import io.github.kemurixd.lurk.emotes.provider.FfzParser
import io.github.kemurixd.lurk.emotes.provider.SevenTvParser
import io.github.kemurixd.lurk.emotes.provider.SevenTvUser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

private const val SEVENTV = "https://7tv.io/v3"
private const val BTTV = "https://api.betterttv.net/3"
private const val FFZ = "https://api.frankerfacez.com/v1"

/**
 * The public emote endpoints. HTTP errors surface as [LurkException]; a 404 on a user or room means the channel has
 * no account with that provider and yields an empty result. A body that is not the expected JSON (an HTML error page,
 * a captive portal, a truncated response) throws [LurkError.SchemaChanged], so callers can tell it apart from a
 * channel that really has no emotes.
 */
class EmoteApi(
    private val http: HttpClient,
) {
    suspend fun sevenTvGlobal(): List<Emote> =
        readable(SevenTvParser.emoteSetOrNull(text("$SEVENTV/emote-sets/global"), EmoteScope.GLOBAL))

    suspend fun sevenTvUser(
        platform: Platform,
        userId: String,
    ): SevenTvUser =
        orMissing(SevenTvUser(null, emptyList())) {
            readable(SevenTvParser.userOrNull(text("$SEVENTV/users/${platform.id}/$userId")))
        }

    suspend fun sevenTvSet(setId: String): List<Emote> =
        readable(SevenTvParser.emoteSetOrNull(text("$SEVENTV/emote-sets/$setId"), EmoteScope.PERSONAL))

    suspend fun bttvGlobal(zw: Set<String>): List<Emote> =
        readable(BttvParser.globalOrNull(text("$BTTV/cached/emotes/global"), zw))

    suspend fun bttvUser(
        twitchId: String,
        zw: Set<String>,
    ): List<Emote> =
        orMissing(emptyList()) { readable(BttvParser.userOrNull(text("$BTTV/cached/users/twitch/$twitchId"), zw)) }

    suspend fun ffzGlobal(): List<Emote> = readable(FfzParser.globalOrNull(text("$FFZ/set/global")))

    suspend fun ffzRoom(twitchId: String): List<Emote> =
        orMissing(emptyList()) { readable(FfzParser.roomOrNull(text("$FFZ/room/id/$twitchId"))) }

    private suspend fun text(url: String): String = http.get(url).bodyAsText()

    /** A parse result, or [LurkError.SchemaChanged] when the parser could not read the body at all. */
    private fun <T : Any> readable(parsed: T?): T = parsed ?: throw LurkException(LurkError.SchemaChanged)

    private suspend fun <T> orMissing(
        missing: T,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: LurkException) {
            if (e.error == LurkError.NotFound) missing else throw e
        }
}

package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.emotes.EmoteChannel
import io.github.kemurixd.lurk.emotes.NativeEmoteSource
import io.github.kemurixd.lurk.platform.twitch.discovery.nonBlank
import io.github.kemurixd.lurk.platform.twitch.discovery.obj
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val EMOTES_OPERATION = "ChannelEmotes"

internal const val EMOTES_QUERY =
    "query ChannelEmotes(\$id: ID!) { user(id: \$id) { subscriptionProducts { emotes { id token } } } }"

private const val IMAGE_BASE = "https://static-cdn.jtvnw.net/emoticons/v2"
private val scales = listOf(1, 2, 3)

/**
 * A channel's own Twitch emotes, read signed out. Global emotes come from the IRC positions, so only the channel's
 * subscription-product emotes are listed. An unknown user, or a 404, is an empty list.
 */
class TwitchEmoteSource(
    private val gql: GqlClient,
) : NativeEmoteSource {
    override suspend fun load(channel: EmoteChannel): List<Emote> {
        val variables = buildJsonObject { put("id", channel.channelId) }
        return when (val result = gql.query(EMOTES_OPERATION, EMOTES_QUERY, variables, null, ::decodeEmotes)) {
            is Outcome.Success -> {
                result.value
            }

            is Outcome.Failure -> {
                if (result.error == LurkError.NotFound) emptyList() else throw LurkException(result.error)
            }
        }
    }
}

internal fun decodeEmotes(data: JsonObject): List<Emote> =
    data
        .obj("user")
        ?.objects("subscriptionProducts")
        .orEmpty()
        .flatMap { it.objects("emotes") }
        .mapNotNull(::emoteOf)
        .distinctBy { it.id }

private fun emoteOf(fields: JsonObject): Emote? {
    val id = fields.nonBlank("id")
    val token = fields.nonBlank("token")
    return if (id == null || token == null) {
        null
    } else {
        Emote(
            id = id,
            name = token,
            provider = EmoteProvider.TWITCH,
            scope = EmoteScope.CHANNEL,
            images = scales.map { EmoteImage(it, "$IMAGE_BASE/$id/default/dark/$it.0") },
        )
    }
}

package io.github.kemurixd.lurk.platform.kick.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.emotes.EmoteChannel
import io.github.kemurixd.lurk.emotes.NativeEmoteSource
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.github.kemurixd.lurk.platform.kick.discovery.decodeOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val IMAGE_BASE = "https://files.kick.com/emotes"
private val globalSets = setOf("Global", "Emojis", "Emoji")

@Serializable
private data class EmoteSetJson(
    val id: JsonPrimitive? = null,
    val name: String? = null,
    val emotes: List<JsonElement>? = null,
)

@Serializable
private data class EmoteJson(
    val id: JsonPrimitive? = null,
    val name: String? = null,
)

/**
 * Kick's own emotes for a channel, read signed out from `kick.com/emotes/{slug}`. For Kick, [EmoteChannel.channelId]
 * is the channel **slug**. The reply is a list of sets: the channel's own, then `Global` and `Emojis`. A 404 is an
 * empty list; any other failure is thrown as a [LurkException].
 */
class KickEmoteSource(
    private val api: KickApi,
) : NativeEmoteSource {
    override suspend fun load(channel: EmoteChannel): List<Emote> =
        when (val reply = api.get(KickBase.SITE, "emotes/${channel.channelId.asPathSegment()}", token = null)) {
            is Outcome.Success -> {
                decodeEmoteSets(reply.value)
            }

            is Outcome.Failure -> {
                if (reply.error == LurkError.NotFound) emptyList() else throw LurkException(reply.error)
            }
        }
}

private fun decodeEmoteSets(root: JsonElement): List<Emote> {
    val sets = root as? JsonArray ?: throw LurkException(LurkError.SchemaChanged)
    return sets.mapNotNull { it.decodeOrNull<EmoteSetJson>() }.flatMap { set ->
        val global = set.name in globalSets || set.id?.contentOrNull in globalSets
        val scope = if (global) EmoteScope.GLOBAL else EmoteScope.CHANNEL
        set.emotes.orEmpty().mapNotNull { it.decodeOrNull<EmoteJson>()?.toEmote(scope) }
    }
}

private fun EmoteJson.toEmote(scope: EmoteScope): Emote? {
    val key = id?.contentOrNull?.takeIf { it.isNotBlank() }
    val token = name?.takeIf { it.isNotBlank() }
    return if (key == null || token == null) {
        null
    } else {
        Emote(
            id = key,
            name = token,
            provider = EmoteProvider.KICK,
            scope = scope,
            images = listOf(EmoteImage(1, "$IMAGE_BASE/$key/fullsize")),
        )
    }
}

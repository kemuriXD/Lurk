package io.github.kemurixd.lurk.platform.kick.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.platform.api.BadgeSource
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.github.kemurixd.lurk.platform.kick.channel.KickImageJson
import io.github.kemurixd.lurk.platform.kick.channel.decodeKickChannel
import io.github.kemurixd.lurk.platform.kick.discovery.decodeOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

@Serializable
private data class SubscriberBadgeJson(
    val months: Int? = null,
    @SerialName("badge_image") val badgeImage: KickImageJson? = null,
)

/**
 * A channel's subscriber badges, keyed `subscriber/<months>`. [load] takes the channel **slug**. The other badges
 * come with each chat message, so nothing else is fetched. A failure is thrown as a [LurkException].
 */
class KickBadgeSource(
    private val api: KickApi,
) : BadgeSource {
    override suspend fun load(channelId: String): Map<String, Badge> =
        when (val reply = api.get(KickBase.API, "v2/channels/${channelId.asPathSegment()}", token = null)) {
            is Outcome.Success -> badgesOf(reply.value)
            is Outcome.Failure -> throw LurkException(reply.error)
        }

    private fun badgesOf(root: JsonElement): Map<String, Badge> {
        val channel =
            try {
                decodeKickChannel(root)
            } catch (e: SerializationException) {
                throw LurkException(LurkError.SchemaChanged, e)
            }
        return channel.subscriberBadges
            .orEmpty()
            .mapNotNull { it.decodeOrNull<SubscriberBadgeJson>() }
            .mapNotNull { item ->
                val months = item.months
                val url = item.badgeImage?.src ?: item.badgeImage?.url
                if (months == null || url == null) null else Badge("subscriber", months.toString(), null, url)
            }.associateBy { "${it.setId}/${it.version}" }
    }
}

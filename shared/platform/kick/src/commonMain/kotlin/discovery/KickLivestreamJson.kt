package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.channel.KickImageJson
import io.github.kemurixd.lurk.platform.kick.channel.startedAt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of a livestream list, as `livestreams` and the search reply give it. */
@Serializable
internal data class KickLiveItemJson(
    val title: String? = null,
    @SerialName("viewer_count") val viewerCount: Long? = null,
    @SerialName("start_time") val startTime: String? = null,
    val thumbnail: KickImageJson? = null,
    val channel: KickLiveChannelJson? = null,
    val category: KickLiveCategoryJson? = null,
)

@Serializable
internal data class KickLiveChannelJson(
    val id: Long? = null,
    val slug: String? = null,
    val username: String? = null,
    @SerialName("profile_pic") val profilePic: String? = null,
)

@Serializable
internal data class KickLiveCategoryJson(
    val id: Long? = null,
    val name: String? = null,
)

/** Null when the entry names no channel. */
internal fun KickLiveItemJson.toStream(): Stream? {
    val login = channel?.slug
    val channelId = channel?.id
    return if (login == null || channelId == null) {
        null
    } else {
        Stream(
            channel = ChannelRef(Platform.KICK, login),
            channelId = channelId.toString(),
            displayName = channel.username ?: login,
            title = title.orEmpty(),
            category = category?.toCategory(),
            viewers = viewerCount ?: 0L,
            startedAt = startedAt(startTime),
            thumbnailUrl = thumbnail?.src ?: thumbnail?.url,
            avatarUrl = channel.profilePic,
        )
    }
}

private fun KickLiveCategoryJson.toCategory(): Category? =
    if (id == null || name == null) null else Category(Platform.KICK, id.toString(), name, null)

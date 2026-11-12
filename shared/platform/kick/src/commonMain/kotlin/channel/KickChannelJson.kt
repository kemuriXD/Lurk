package io.github.kemurixd.lurk.platform.kick.channel

import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

private val kickChannelJson = Json { ignoreUnknownKeys = true }

/** Lenient reading of the `channels/{slug}` reply: every field is optional and unknown keys are ignored. */
@Serializable
internal data class KickChannelJson(
    val id: Long? = null,
    val slug: String? = null,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("followers_count") val followersCount: JsonPrimitive? = null,
    @SerialName("banner_image") val bannerImage: KickImageJson? = null,
    val livestream: KickLivestreamJson? = null,
    @SerialName("recent_categories") val recentCategories: List<KickCategoryJson>? = null,
    val user: KickUserJson? = null,
    val chatroom: KickChatroomJson? = null,
    @SerialName("subscriber_badges") val subscriberBadges: List<JsonElement>? = null,
)

@Serializable
internal data class KickImageJson(
    val url: String? = null,
    val src: String? = null,
)

@Serializable
internal data class KickUserJson(
    val username: String? = null,
    val bio: String? = null,
    @SerialName("profile_pic") val profilePic: String? = null,
)

@Serializable
internal data class KickChatroomJson(
    val id: Long? = null,
)

@Serializable
internal data class KickCategoryJson(
    val id: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val name: String? = null,
    val viewers: Long? = null,
    val banner: KickImageJson? = null,
)

@Serializable
internal data class KickLivestreamJson(
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("viewer_count") val viewerCount: Long? = null,
    val thumbnail: KickImageJson? = null,
    val categories: List<KickCategoryJson>? = null,
)

internal fun decodeKickChannel(element: JsonElement): KickChannelJson =
    kickChannelJson.decodeFromJsonElement(KickChannelJson.serializer(), element)

/**
 * A category from either list. `id` is the subcategory (what the live list filters on); `category_id` is its parent
 * group and is not used.
 */
internal fun KickCategoryJson.toCategory(): Category? =
    if (name == null || id == null) {
        null
    } else {
        Category(Platform.KICK, id.toString(), name, banner?.src ?: banner?.url, viewers)
    }

/** Kick sends `2026-01-01 00:00:00` in UTC; the web API sends ISO times with a `Z` or an offset. */
internal fun startedAt(text: String?): Instant? =
    runCatching {
        text?.let {
            val iso = it.trim().replace(' ', 'T')
            Instant.parse(if (iso.endsWith("Z") || '+' in iso) iso else iso + "Z")
        }
    }.getOrNull()

/** The live broadcast, or null when the channel is offline. */
internal fun KickChannelJson.toStream(): Stream? {
    val live = livestream?.takeIf { it.isLive != false }
    val login = slug
    val channelId = id
    return if (live == null || login == null || channelId == null) {
        null
    } else {
        Stream(
            channel = ChannelRef(Platform.KICK, login),
            channelId = channelId.toString(),
            displayName = user?.username ?: login,
            title = live.sessionTitle.orEmpty(),
            category = live.categories?.firstNotNullOfOrNull { it.toCategory() },
            viewers = live.viewerCount ?: 0L,
            startedAt = startedAt(live.startTime),
            thumbnailUrl = live.thumbnail?.url,
            avatarUrl = user?.profilePic,
        )
    }
}

/** Null when the reply lacks the slug, channel ID or chatroom ID. */
internal fun KickChannelJson.toChannel(): Channel? {
    val login = slug
    val channelId = id
    val chatroomId = chatroom?.id
    if (login == null || channelId == null || chatroomId == null) return null
    val stream = toStream()
    val recent = recentCategories?.firstNotNullOfOrNull { it.toCategory() }
    return Channel(
        ref = ChannelRef(Platform.KICK, login),
        id = channelId.toString(),
        chatId = chatroomId.toString(),
        displayName = user?.username ?: login,
        avatarUrl = user?.profilePic,
        bannerUrl = bannerImage?.url,
        bio = user?.bio,
        followers = followersCount?.content?.toLongOrNull(),
        live = stream,
        lastStream = if (stream == null && recent != null) LastStream(null, recent, null) else null,
    )
}

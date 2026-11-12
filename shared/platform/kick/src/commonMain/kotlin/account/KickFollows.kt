package io.github.kemurixd.lurk.platform.kick.account

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.discovery.asObject
import io.github.kemurixd.lurk.platform.kick.discovery.decode
import io.github.kemurixd.lurk.platform.kick.discovery.decodeOrNull
import io.github.kemurixd.lurk.platform.kick.discovery.requireArray
import io.github.kemurixd.lurk.platform.kick.discovery.text
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal const val FOLLOWED_MAX_PAGES = 20

/** One followed channel as the follows list gives it. It names no IDs, no thumbnail and no start time. */
@Serializable
private data class FollowedItemJson(
    @SerialName("is_live") val isLive: Boolean? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("channel_slug") val channelSlug: String? = null,
    @SerialName("viewer_count") val viewerCount: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("user_username") val userUsername: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
)

private class FollowedPage(
    val items: List<FollowedItemJson>,
    val nextCursor: String?,
)

/**
 * Everything the signed-in user follows: live ones as streams by viewers, the rest as channels A to Z. Reads at
 * most [FOLLOWED_MAX_PAGES] pages. Signed out is `NotSignedIn`; there is no anonymous answer.
 *
 * Kick's list has no channel or category IDs, so `channelId`, `Channel.id`, `chatId` and `Category.id` are empty
 * here; `channel` has the real ones.
 */
internal suspend fun KickApi.followed(sessions: SessionRepository): Outcome<Followed> =
    sessions.authed(Platform.KICK, anonymousFallback = false) { token ->
        if (token == null) return@authed Outcome.Failure(LurkError.NotSignedIn)
        val items = mutableListOf<FollowedItemJson>()
        var cursor: String? = null
        var pages = 0
        do {
            val query = if (cursor == null) emptyMap() else mapOf("cursor" to cursor)
            when (val result = get(KickBase.API, "v2/channels/followed", token, query).decode(::decodeFollowed)) {
                is Outcome.Failure -> {
                    return@authed result
                }

                is Outcome.Success -> {
                    items += result.value.items
                    cursor = result.value.nextCursor
                }
            }
            pages++
        } while (cursor != null && pages < FOLLOWED_MAX_PAGES)
        Outcome.Success(split(items))
    }

private fun decodeFollowed(root: JsonElement): FollowedPage {
    val reply = root.asObject()
    val items = reply.requireArray("channels").mapNotNull { it.decodeOrNull<FollowedItemJson>() }
    return FollowedPage(items, reply.text("nextCursor")?.takeIf { it.isNotBlank() })
}

private fun split(items: List<FollowedItemJson>): Followed {
    val live = mutableListOf<Stream>()
    val offline = mutableListOf<Channel>()
    for (item in items) {
        val slug = item.channelSlug?.takeIf { it.isNotBlank() } ?: continue
        if (item.isLive == true) live += item.toStream(slug) else offline += item.toChannel(slug)
    }
    return Followed(
        live = live.sortedByDescending { it.viewers },
        offline = offline.sortedBy { it.displayName.lowercase() },
    )
}

private fun FollowedItemJson.toStream(slug: String): Stream =
    Stream(
        channel = ChannelRef(Platform.KICK, slug),
        channelId = "",
        displayName = userUsername ?: slug,
        title = sessionTitle.orEmpty(),
        category = categoryName?.takeIf { it.isNotBlank() }?.let { Category(Platform.KICK, "", it, null) },
        viewers = viewerCount ?: 0L,
        startedAt = null,
        thumbnailUrl = null,
        avatarUrl = profilePicture,
    )

private fun FollowedItemJson.toChannel(slug: String): Channel =
    Channel(
        ref = ChannelRef(Platform.KICK, slug),
        id = "",
        chatId = "",
        displayName = userUsername ?: slug,
        avatarUrl = profilePicture,
        bannerUrl = null,
        bio = null,
        followers = null,
        live = null,
        lastStream = null,
    )

/** Follows or unfollows [ref] for the signed-in user. Signed out is `NotSignedIn`, before any request. */
internal suspend fun KickApi.setFollow(
    sessions: SessionRepository,
    ref: ChannelRef,
    follow: Boolean,
): Outcome<Unit> =
    sessions.authed(Platform.KICK, anonymousFallback = false) { token ->
        if (token == null) {
            Outcome.Failure(LurkError.NotSignedIn)
        } else {
            val method = if (follow) HttpMethod.Post else HttpMethod.Delete
            send(method, KickBase.API, "v2/channels/${ref.login}/follow", token, body = null).map { }
        }
    }

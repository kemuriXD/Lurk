package io.github.kemurixd.lurk.platform.kick.account

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.github.kemurixd.lurk.platform.kick.channel.LiveDetails
import io.github.kemurixd.lurk.platform.kick.channel.LiveDetailsMemo
import io.github.kemurixd.lurk.platform.kick.channel.liveDetails
import io.github.kemurixd.lurk.platform.kick.channel.withDetails
import io.github.kemurixd.lurk.platform.kick.discovery.asObject
import io.github.kemurixd.lurk.platform.kick.discovery.decode
import io.github.kemurixd.lurk.platform.kick.discovery.decodeOrNull
import io.github.kemurixd.lurk.platform.kick.discovery.requireArray
import io.github.kemurixd.lurk.platform.kick.discovery.text
import io.ktor.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.seconds

internal const val FOLLOWED_MAX_PAGES = 20

/** How many live follows have their `livestream` details read at once. */
internal const val LIVE_DETAILS_PARALLEL = 4

/** How long one `livestream` lookup may take before its card is left bare, so one slow channel can't hold the list. */
internal val LIVE_DETAILS_TIMEOUT = 5.seconds

/**
 * One followed channel as the follows list gives it. It names no IDs, no thumbnail, no category ID and no start time;
 * live ones get those from [liveDetails].
 */
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
 * Kick's list has no channel IDs, so `Stream.channelId`, `Channel.id` and `chatId` are empty here; `channel` has the
 * real values. It has no preview, category ID or start time either, so each live stream costs one
 * `channels/{slug}/livestream` request, at most [LIVE_DETAILS_PARALLEL] at a time and each cut off after
 * [LIVE_DETAILS_TIMEOUT]. A stream whose request fails or times out keeps no preview and no category, and the list
 * still loads. With a [memo], an answer is reused for its window instead of asked again.
 */
internal suspend fun KickApi.followed(
    sessions: SessionRepository,
    memo: LiveDetailsMemo? = null,
): Outcome<Followed> =
    sessions.authed(Platform.KICK, anonymousFallback = false) { token ->
        if (token == null) return@authed Outcome.Failure(LurkError.NotSignedIn)
        val items = mutableListOf<FollowedItemJson>()
        var cursor: String? = null
        var pages = 0
        do {
            val current = cursor
            val query = if (current == null) emptyMap() else mapOf("cursor" to current)
            when (val result = get(KickBase.API, "v2/channels/followed", token, query).decode(::decodeFollowed)) {
                is Outcome.Failure -> {
                    return@authed result
                }

                is Outcome.Success -> {
                    items += result.value.items
                    cursor = result.value.nextCursor?.takeIf { it != current }
                }
            }
            pages++
        } while (cursor != null && pages < FOLLOWED_MAX_PAGES)
        val followed = split(items)
        Outcome.Success(followed.copy(live = withLiveDetails(followed.live, token, memo)))
    }

private suspend fun KickApi.withLiveDetails(
    live: List<Stream>,
    token: String,
    memo: LiveDetailsMemo?,
): List<Stream> {
    val permits = Semaphore(LIVE_DETAILS_PARALLEL)
    return coroutineScope {
        live
            .map { stream ->
                async { stream.withDetails(detailsOf(stream.channel.login, token, memo, permits)) }
            }.awaitAll()
    }
}

private suspend fun KickApi.detailsOf(
    slug: String,
    token: String,
    memo: LiveDetailsMemo?,
    permits: Semaphore,
): LiveDetails? =
    memo?.get(slug)
        ?: permits
            .withPermit { withTimeoutOrNull(LIVE_DETAILS_TIMEOUT) { liveDetails(token, slug) } }
            ?.also { memo?.put(slug, it) }

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

/**
 * A live followed channel. The list gives a category name but no category ID, so [Stream.category] is null rather
 * than a category with an empty ID. [Stream.channelId] is empty: the ID is not in the list. Resolve `channel(ref)`
 * before chat or anything that needs an ID.
 */
private fun FollowedItemJson.toStream(slug: String): Stream =
    Stream(
        channel = ChannelRef(Platform.KICK, slug),
        channelId = "",
        displayName = userUsername ?: slug,
        title = sessionTitle.orEmpty(),
        category = null,
        viewers = viewerCount ?: 0L,
        startedAt = null,
        thumbnailUrl = null,
        avatarUrl = profilePicture,
    )

/**
 * An offline followed channel. `id` and `chatId` are empty: the list has neither. Resolve `channel(ref)` before
 * chat.
 */
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
            send(method, KickBase.API, "v2/channels/${ref.login.asPathSegment()}/follow", token, body = null).map { }
        }
    }

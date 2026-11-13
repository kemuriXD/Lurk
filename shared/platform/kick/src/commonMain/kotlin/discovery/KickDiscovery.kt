package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.kick.account.LIVE_DETAILS_TIMEOUT
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.github.kemurixd.lurk.platform.kick.channel.KickCategoryJson
import io.github.kemurixd.lurk.platform.kick.channel.decodeKickChannel
import io.github.kemurixd.lurk.platform.kick.channel.latestPastStream
import io.github.kemurixd.lurk.platform.kick.channel.liveDetails
import io.github.kemurixd.lurk.platform.kick.channel.toCategory
import io.github.kemurixd.lurk.platform.kick.channel.toChannel
import io.github.kemurixd.lurk.platform.kick.channel.withDetails
import io.github.kemurixd.lurk.platform.kick.channel.withLastStream
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

private const val LIVE_PAGE_SIZE = "24"
private const val CATEGORY_PAGE_SIZE = "32"
private const val FIRST_CATEGORY_PAGE = "1"
private const val VIEWERS_DESC = "viewer_count_desc"
private const val FEATURED = "featured"

/** One page of the live streams with the most viewers. The cursor is the previous page's `nextCursor`. */
internal suspend fun KickApi.topLive(
    sessions: SessionRepository,
    cursor: String?,
): Outcome<Page<Stream>> = livestreams(sessions, categoryId = null, cursor = cursor, sort = VIEWERS_DESC)

/**
 * One page of the live streams in a category, in [sort] order. [categoryId] is `Category.id`. A blank ID is
 * `NotFound` with no request: Kick would answer an empty filter with the unfiltered list.
 *
 * Kick's site has three sorts (probed 2026-11-13). Featured and Viewers (High to Low) are `sort=featured` and
 * `sort=viewer_count_desc` on the livestreams list and work signed out. Recommended is a separate list that answers
 * 403 without a session, so signed out it is `NotSignedIn` without a request. Kick also accepts `viewer_count_asc` but
 * returns the high-to-low order for it, so it is not offered. Other sorts are `Unsupported`.
 */
internal suspend fun KickApi.categoryLive(
    sessions: SessionRepository,
    categoryId: String,
    cursor: String?,
    sort: CategorySort,
): Outcome<Page<Stream>> =
    when {
        categoryId.isBlank() -> Outcome.Failure(LurkError.NotFound)
        sort == CategorySort.FEATURED -> livestreams(sessions, categoryId, cursor, FEATURED)
        sort == CategorySort.VIEWERS_HIGH -> livestreams(sessions, categoryId, cursor, VIEWERS_DESC)
        sort == CategorySort.RECOMMENDED -> recommended(sessions, categoryId, cursor)
        else -> Outcome.Failure(LurkError.Unsupported)
    }

private suspend fun KickApi.livestreams(
    sessions: SessionRepository,
    categoryId: String?,
    cursor: String?,
    sort: String,
): Outcome<Page<Stream>> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        val query =
            buildMap {
                put("limit", LIVE_PAGE_SIZE)
                put("sort", sort)
                if (categoryId != null) put("category_id", categoryId)
                if (cursor != null) put("after", cursor)
            }
        get(KickBase.WEB, "livestreams", token, query).decode(::decodeLivestreams)
    }

/** The signed-in user's recommended streams in a category. Same reply shape as the livestreams list. */
private suspend fun KickApi.recommended(
    sessions: SessionRepository,
    categoryId: String,
    cursor: String?,
): Outcome<Page<Stream>> =
    sessions.authed(Platform.KICK, anonymousFallback = false) { token ->
        if (token == null) return@authed Outcome.Failure(LurkError.NotSignedIn)
        val query =
            buildMap {
                put("limit", LIVE_PAGE_SIZE)
                put("category_id", categoryId)
                if (cursor != null) put("cursor", cursor)
            }
        get(KickBase.WEB, "recommendations/livestreams/list", token, query).decode(::decodeLivestreams)
    }

private fun decodeLivestreams(root: JsonElement): Page<Stream> {
    val data = root.asObject().requireObject("data")
    val streams = data.requireArray("livestreams").mapNotNull { it.decodeOrNull<KickLiveItemJson>()?.toStream() }
    val next = (data["pagination"] as? JsonObject)?.text("next_cursor")
    return Page(streams, next?.takeIf { it.isNotBlank() })
}

/** One page of categories, most viewers first. The cursor is the page number. */
internal suspend fun KickApi.categories(
    sessions: SessionRepository,
    cursor: String?,
): Outcome<Page<Category>> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        val query = mapOf("limit" to CATEGORY_PAGE_SIZE, "page" to (cursor ?: FIRST_CATEGORY_PAGE))
        get(KickBase.API, "v1/subcategories", token, query).decode(::decodeCategories)
    }

private fun decodeCategories(root: JsonElement): Page<Category> {
    val reply = root.asObject()
    val categories = reply.requireArray("data").mapNotNull { it.decodeOrNull<KickCategoryJson>()?.toCategory() }
    val current = reply.int("current_page")
    val last = reply.int("last_page")
    val next = if (current != null && last != null && current < last) (current + 1).toString() else null
    return Page(categories, next)
}

/**
 * When [ref] last went live: the start of its newest finished broadcast, from `channels/{slug}/videos`. Null when it
 * has none. Lists that lack the time ask this per row on screen.
 */
internal suspend fun KickApi.lastLive(
    sessions: SessionRepository,
    ref: ChannelRef,
): Outcome<Instant?> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        get(KickBase.API, "v2/channels/${ref.login.asPathSegment()}/videos", token)
            .decode(::latestPastStream)
            .map { it?.startedAt }
    }

/**
 * A channel's profile, with its stream when it is live. A slug Kick does not know is `NotFound`.
 *
 * With [extras], the channel page's second request follows. A live channel asks `channels/{slug}/livestream` for its
 * preview, which the profile lacks. An offline channel asks for its past broadcasts, since the profile doesn't say
 * when it was last live. If the second request fails, the channel still loads without that detail. Callers that only
 * need the IDs, such as chat, pass `extras = false`.
 */
internal suspend fun KickApi.channel(
    sessions: SessionRepository,
    ref: ChannelRef,
    extras: Boolean = true,
): Outcome<Channel> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        val path = "v2/channels/${ref.login.asPathSegment()}"
        val result =
            get(KickBase.API, path, token).decode {
                decodeKickChannel(it).toChannel() ?: throw LurkException(LurkError.SchemaChanged)
            }
        val channel = (result as? Outcome.Success)?.value
        val live = channel?.live
        when {
            !extras || channel == null -> {
                result
            }

            live != null -> {
                val details = withTimeoutOrNull(LIVE_DETAILS_TIMEOUT) { liveDetails(token, ref.login) }
                Outcome.Success(channel.copy(live = live.withDetails(details)))
            }

            else -> {
                val videos = get(KickBase.API, "$path/videos", token).decode(::latestPastStream)
                val past = (videos as? Outcome.Success)?.value
                Outcome.Success(past?.let(channel::withLastStream) ?: channel)
            }
        }
    }

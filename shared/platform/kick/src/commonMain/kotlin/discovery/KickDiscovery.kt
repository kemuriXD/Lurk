package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.channel.KickCategoryJson
import io.github.kemurixd.lurk.platform.kick.channel.decodeKickChannel
import io.github.kemurixd.lurk.platform.kick.channel.toCategory
import io.github.kemurixd.lurk.platform.kick.channel.toChannel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val LIVE_PAGE_SIZE = "24"
private const val CATEGORY_PAGE_SIZE = "32"
private const val FIRST_CATEGORY_PAGE = "1"

/** One page of the live streams with the most viewers. The cursor is the previous page's `nextCursor`. */
internal suspend fun KickApi.topLive(
    sessions: SessionRepository,
    cursor: String?,
): Outcome<Page<Stream>> = livestreams(sessions, categoryId = null, cursor = cursor)

/** One page of the live streams in a category, most viewers first. [categoryId] is `Category.id`. */
internal suspend fun KickApi.categoryLive(
    sessions: SessionRepository,
    categoryId: String,
    cursor: String?,
): Outcome<Page<Stream>> = livestreams(sessions, categoryId, cursor)

private suspend fun KickApi.livestreams(
    sessions: SessionRepository,
    categoryId: String?,
    cursor: String?,
): Outcome<Page<Stream>> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        val query =
            buildMap {
                put("limit", LIVE_PAGE_SIZE)
                put("sort", "viewer_count_desc")
                if (categoryId != null) put("category_id", categoryId)
                if (cursor != null) put("after", cursor)
            }
        get(KickBase.WEB, "livestreams", token, query).decode(::decodeLivestreams)
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

/** A channel's profile, with its stream when it is live. A slug Kick does not know is `NotFound`. */
internal suspend fun KickApi.channel(
    sessions: SessionRepository,
    ref: ChannelRef,
): Outcome<Channel> =
    sessions.authed(Platform.KICK, anonymousFallback = true) { token ->
        get(KickBase.API, "v2/channels/${ref.login}", token).decode {
            decodeKickChannel(it).toChannel() ?: throw LurkException(LurkError.SchemaChanged)
        }
    }

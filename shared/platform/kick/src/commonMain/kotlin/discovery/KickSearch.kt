package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.channel.KickImageJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
private data class SearchChannelJson(
    val id: Long? = null,
    val slug: String? = null,
    val username: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("follower_count") val followerCount: Long? = null,
    @SerialName("is_live") val isLive: Boolean? = null,
)

@Serializable
private data class SearchCategoryJson(
    val id: Long? = null,
    val name: String? = null,
    val thumbnail: KickImageJson? = null,
    @SerialName("viewer_count") val viewerCount: Long? = null,
)

/**
 * Channels and categories matching [query], from the search host the web app uses. The request is always signed
 * out, so no token goes to that host. Live channels come before offline ones.
 *
 * The reply carries no chatroom ID, so a channel's `chatId` is empty here; `channel` fills it in.
 */
internal suspend fun KickApi.search(query: String): Outcome<SearchResults> =
    get(KickBase.SEARCH, "search/enriched", token = null, query = mapOf("query" to query)).decode(::decodeSearch)

private fun decodeSearch(root: JsonElement): SearchResults {
    val data = root.asObject().requireObject("data")
    val streams =
        data
            .arrayOrEmpty("livestreams")
            .mapNotNull { it.decodeOrNull<KickLiveItemJson>()?.toStream() }
            .associateBy { it.channel.login }
    val channels =
        data
            .requireArray("channels")
            .mapNotNull { it.decodeOrNull<SearchChannelJson>() }
            // sortedByDescending is stable, so Kick's order holds within the live and offline groups.
            .sortedByDescending { it.isLive == true }
            .mapNotNull { it.toChannel(streams) }
    val categories = data.requireArray("categories").mapNotNull { it.decodeOrNull<SearchCategoryJson>()?.toCategory() }
    return SearchResults(channels, categories)
}

private fun SearchChannelJson.toChannel(streams: Map<String, Stream>): Channel? =
    if (id == null || slug == null) {
        null
    } else {
        Channel(
            ref = ChannelRef(Platform.KICK, slug),
            id = id.toString(),
            chatId = "",
            displayName = username ?: slug,
            avatarUrl = profilePicture,
            bannerUrl = null,
            bio = null,
            followers = followerCount,
            live = if (isLive == true) streams[slug] else null,
            lastStream = null,
        )
    }

private fun SearchCategoryJson.toCategory(): Category? =
    if (id == null || name == null) {
        null
    } else {
        Category(Platform.KICK, id.toString(), name, thumbnail?.src ?: thumbnail?.url, viewerCount)
    }

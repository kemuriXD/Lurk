package io.github.kemurixd.lurk.platform.kick.channel

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
private data class KickLiveDetailsJson(
    val thumbnail: KickImageJson? = null,
    val category: KickLiveDetailsCategoryJson? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class KickLiveDetailsCategoryJson(
    val id: Long? = null,
    val name: String? = null,
)

/** What `channels/{slug}/livestream` adds to a live stream that the other replies leave out or get wrong. */
internal data class LiveDetails(
    val thumbnailUrl: String?,
    val category: Category?,
    val startedAt: Instant?,
)

/**
 * The current broadcast's preview, category and start time, from `channels/{slug}/livestream`. Null when the channel
 * is no longer live (`{"data":null}`) or the request fails, so a caller keeps what it had.
 *
 * Only `thumbnail.src` is read. The channel reply's `thumbnail.url` points at `stream.kick.com`, which refuses every
 * request.
 */
internal suspend fun KickApi.liveDetails(
    token: String?,
    slug: String,
): LiveDetails? {
    val reply = get(KickBase.API, "v2/channels/${slug.asPathSegment()}/livestream", token)
    val data = ((reply as? Outcome.Success)?.value as? JsonObject)?.get("data") as? JsonObject
    return data
        ?.let {
            runCatching {
                kickChannelJson.decodeFromJsonElement(
                    KickLiveDetailsJson.serializer(),
                    it,
                )
            }.getOrNull()
        }?.toDetails()
}

private fun KickLiveDetailsJson.toDetails(): LiveDetails {
    val id = category?.id
    val name = category?.name
    return LiveDetails(
        thumbnailUrl = thumbnail?.src?.takeIf { it.isNotBlank() },
        category = if (id == null || name == null) null else Category(Platform.KICK, id.toString(), name, null),
        startedAt = startedAt(createdAt),
    )
}

/** This stream with [details] filled in: the preview always, the category and start time only where missing. */
internal fun Stream.withDetails(details: LiveDetails?): Stream =
    if (details == null) {
        this
    } else {
        copy(
            thumbnailUrl = details.thumbnailUrl,
            category = category ?: details.category,
            startedAt = startedAt ?: details.startedAt,
        )
    }

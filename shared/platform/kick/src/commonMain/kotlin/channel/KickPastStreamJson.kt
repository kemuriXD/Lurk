package io.github.kemurixd.lurk.platform.kick.channel

import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.LastStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/** One past broadcast from `channels/{slug}/videos`. Only what the offline channel shows is read. */
@Serializable
private data class KickPastStreamJson(
    @SerialName("is_live") val isLive: Boolean? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
)

/** A finished broadcast: when it started and its title. */
internal data class PastStream(
    val title: String?,
    val startedAt: Instant,
)

/** The newest finished broadcast in a `channels/{slug}/videos` reply, or null when there is none to read. */
internal fun latestPastStream(root: JsonElement): PastStream? =
    (root as? JsonArray)
        .orEmpty()
        .mapNotNull { item ->
            runCatching { kickChannelJson.decodeFromJsonElement(KickPastStreamJson.serializer(), item) }.getOrNull()
        }.filter { it.isLive != true }
        .mapNotNull { past -> startedAt(past.startTime)?.let { PastStream(past.sessionTitle, it) } }
        .maxByOrNull { it.startedAt }

/** This channel with [past] as its last stream. The category stays the one from `recent_categories`. */
internal fun Channel.withLastStream(past: PastStream): Channel =
    copy(
        lastStream =
            LastStream(
                title = lastStream?.title ?: past.title,
                category = lastStream?.category,
                startedAt = past.startedAt,
            ),
    )

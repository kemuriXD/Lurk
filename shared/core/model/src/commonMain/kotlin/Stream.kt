package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A live broadcast. [tags] are the broadcaster's own tags, in their order; empty when the platform sends none.
 * [language] is the broadcast language as an ISO 639-1 code, lower case, or null when unknown.
 */
@Serializable
data class Stream(
    val channel: ChannelRef,
    val channelId: String,
    val displayName: String,
    val title: String,
    val category: Category?,
    val viewers: Long,
    val startedAt: Instant?,
    val thumbnailUrl: String?,
    val avatarUrl: String?,
    val tags: List<String> = emptyList(),
    val language: String? = null,
)

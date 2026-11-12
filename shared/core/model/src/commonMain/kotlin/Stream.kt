package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A live broadcast. */
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
)

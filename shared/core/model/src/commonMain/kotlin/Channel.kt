package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** What a channel last streamed, shown while it is offline. */
@Serializable
data class LastStream(
    val title: String?,
    val category: Category?,
    val startedAt: Instant?,
)

/**
 * A channel's profile.
 *
 * [chatId] is what the chat transport and sender address: the login on Twitch, the chatroom ID on Kick.
 */
@Serializable
data class Channel(
    val ref: ChannelRef,
    val id: String,
    val chatId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val bio: String?,
    val followers: Long?,
    val live: Stream?,
    val lastStream: LastStream?,
)

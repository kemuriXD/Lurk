package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.emotes.NativeEmoteSource
import io.github.kemurixd.lurk.playback.PlaybackSource

/** The one interface feature code uses to talk to a streaming platform. */
interface StreamingPlatform {
    val id: Platform
    val capabilities: Capabilities
    val playback: PlaybackSource
    val nativeEmotes: NativeEmoteSource
    val badges: BadgeSource
    val cheermotes: CheermoteSource

    suspend fun topLive(cursor: String?): Outcome<Page<Stream>>

    suspend fun categories(cursor: String?): Outcome<Page<Category>>

    suspend fun liveInCategory(
        categoryId: String,
        cursor: String?,
    ): Outcome<Page<Stream>>

    suspend fun search(query: String): Outcome<SearchResults>

    suspend fun channel(ref: ChannelRef): Outcome<Channel>

    /** `Failure(NotSignedIn)` when signed out. */
    suspend fun followed(): Outcome<Followed>

    suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Outcome<Unit>

    suspend fun chat(channel: Channel): ChatEndpoints
}

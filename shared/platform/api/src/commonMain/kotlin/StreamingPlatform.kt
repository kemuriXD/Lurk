package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.emotes.NativeEmoteSource
import io.github.kemurixd.lurk.playback.PlaybackSource
import kotlin.time.Instant

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

    /**
     * One page of the live streams in a category, in [sort] order. A [sort] missing from `capabilities.categorySorts`
     * is `Failure(Unsupported)`, and one that needs sign-in is `Failure(NotSignedIn)` when signed out.
     */
    suspend fun liveInCategory(
        categoryId: String,
        cursor: String?,
        sort: CategorySort,
    ): Outcome<Page<Stream>>

    suspend fun search(query: String): Outcome<SearchResults>

    suspend fun channel(ref: ChannelRef): Outcome<Channel>

    /** `Failure(NotSignedIn)` when signed out. */
    suspend fun followed(): Outcome<Followed>

    suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Outcome<Unit>

    /**
     * When [ref] last went live, or null when the platform has no record of it. One request per call, so callers look
     * up only the rows on screen and cache the answer. Platforms without `capabilities.lastLiveLookup` already put the
     * time in their list replies and answer `Failure(Unsupported)`.
     */
    suspend fun lastLive(ref: ChannelRef): Outcome<Instant?> = Outcome.Failure(LurkError.Unsupported)

    /** May throw `LurkException` when the channel can't be resolved, as Kick does for followed or search channels. */
    suspend fun chat(channel: Channel): ChatEndpoints
}

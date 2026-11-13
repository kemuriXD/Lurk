package io.github.kemurixd.lurk.feature.testing

import io.github.kemurixd.lurk.core.common.LurkError
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
import io.github.kemurixd.lurk.platform.api.BadgeSource
import io.github.kemurixd.lurk.platform.api.Capabilities
import io.github.kemurixd.lurk.platform.api.ChatEndpoints
import io.github.kemurixd.lurk.platform.api.CheermoteSource
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.playback.AdPolicy
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.github.kemurixd.lurk.playback.PlaybackSource

/**
 * A [StreamingPlatform] whose calls are plain lambdas that tests swap out. Each call defaults to an empty success
 * (a stand-in profile for [channel]) and counts how often it ran. [chat] is not used by the list screens and throws.
 */
class FakeStreamingPlatform(
    override val id: Platform,
    override val capabilities: Capabilities = capabilitiesOf(id),
) : StreamingPlatform {
    var topLive: suspend (cursor: String?) -> Outcome<Page<Stream>> = { Outcome.Success(Page(emptyList(), null)) }
    var categories: suspend (cursor: String?) -> Outcome<Page<Category>> = { Outcome.Success(Page(emptyList(), null)) }
    var liveInCategory: suspend (categoryId: String, cursor: String?) -> Outcome<Page<Stream>> =
        { _, _ -> Outcome.Success(Page(emptyList(), null)) }
    var search: suspend (query: String) -> Outcome<SearchResults> =
        { Outcome.Success(SearchResults(emptyList(), emptyList())) }
    var channel: suspend (ref: ChannelRef) -> Outcome<Channel> =
        { Outcome.Success(testChannel(it.platform, login = it.login)) }
    var followed: suspend () -> Outcome<Followed> = { Outcome.Success(Followed(emptyList(), emptyList())) }
    var setFollow: suspend (channel: Channel, follow: Boolean) -> Outcome<Unit> = { _, _ -> Outcome.Success(Unit) }

    var topLiveCalls = 0
        private set
    var categoriesCalls = 0
        private set
    var liveInCategoryCalls = 0
        private set
    var searchCalls = 0
        private set
    var channelCalls = 0
        private set
    var followedCalls = 0
        private set
    var setFollowCalls = 0
        private set

    override val playback: PlaybackSource = NoPlayback(id)
    override val nativeEmotes: NativeEmoteSource = NativeEmoteSource { emptyList() }
    override val badges: BadgeSource = BadgeSource { emptyMap() }
    override val cheermotes: CheermoteSource = CheermoteSource { emptyList() }

    override suspend fun topLive(cursor: String?): Outcome<Page<Stream>> {
        topLiveCalls++
        return topLive.invoke(cursor)
    }

    override suspend fun categories(cursor: String?): Outcome<Page<Category>> {
        categoriesCalls++
        return categories.invoke(cursor)
    }

    override suspend fun liveInCategory(
        categoryId: String,
        cursor: String?,
    ): Outcome<Page<Stream>> {
        liveInCategoryCalls++
        return liveInCategory.invoke(categoryId, cursor)
    }

    override suspend fun search(query: String): Outcome<SearchResults> {
        searchCalls++
        return search.invoke(query)
    }

    override suspend fun channel(ref: ChannelRef): Outcome<Channel> {
        channelCalls++
        return channel.invoke(ref)
    }

    override suspend fun followed(): Outcome<Followed> {
        followedCalls++
        return followed.invoke()
    }

    override suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Outcome<Unit> {
        setFollowCalls++
        return setFollow.invoke(channel, follow)
    }

    override suspend fun chat(channel: Channel): ChatEndpoints =
        throw UnsupportedOperationException("chat is not faked")

    private class NoPlayback(
        override val platform: Platform,
    ) : PlaybackSource {
        override val adPolicy: AdPolicy = AdPolicy.UNTOUCHED

        override suspend fun grant(channel: ChannelRef): Outcome<PlaybackGrant> = Outcome.Failure(LurkError.Unsupported)
    }

    companion object {
        /** What the real platforms report: Twitch can't follow through the API, and Kick has no cheermotes. */
        fun capabilitiesOf(platform: Platform): Capabilities =
            Capabilities(
                follows = true,
                followMutation = platform == Platform.KICK,
                chatSend = true,
                chatHistory = true,
                cheermotes = platform == Platform.TWITCH,
            )
    }
}

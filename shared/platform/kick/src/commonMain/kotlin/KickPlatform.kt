package io.github.kemurixd.lurk.platform.kick

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.chat.ChatChannel
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.emotes.NativeEmoteSource
import io.github.kemurixd.lurk.platform.api.BadgeSource
import io.github.kemurixd.lurk.platform.api.Capabilities
import io.github.kemurixd.lurk.platform.api.ChatEndpoints
import io.github.kemurixd.lurk.platform.api.CheermoteSource
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.platform.kick.account.followed
import io.github.kemurixd.lurk.platform.kick.account.setFollow
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.assets.KickBadgeSource
import io.github.kemurixd.lurk.platform.kick.assets.KickEmoteSource
import io.github.kemurixd.lurk.platform.kick.chat.KickHistorySource
import io.github.kemurixd.lurk.platform.kick.chat.KickSender
import io.github.kemurixd.lurk.platform.kick.discovery.categories
import io.github.kemurixd.lurk.platform.kick.discovery.categoryLive
import io.github.kemurixd.lurk.platform.kick.discovery.channel
import io.github.kemurixd.lurk.platform.kick.discovery.search
import io.github.kemurixd.lurk.platform.kick.discovery.topLive
import io.github.kemurixd.lurk.platform.kick.realtime.CentrifugoTransport
import io.github.kemurixd.lurk.platform.kick.realtime.RealtimeBootstrap
import io.github.kemurixd.lurk.playback.PlaybackSource
import kotlin.time.Clock

/** Kick behind [StreamingPlatform]. */
class KickPlatform(
    private val api: KickApi,
    private val sessions: SessionRepository,
    private val bootstrap: RealtimeBootstrap,
    private val connector: WebSocketConnector,
    override val playback: PlaybackSource,
    private val clock: Clock = Clock.System,
) : StreamingPlatform {
    override val id: Platform = Platform.KICK
    override val capabilities =
        Capabilities(
            follows = true,
            followMutation = true,
            chatSend = true,
            chatHistory = true,
            cheermotes = false,
        )
    override val nativeEmotes: NativeEmoteSource = KickEmoteSource(api)
    override val badges: BadgeSource = KickBadgeSource(api)
    override val cheermotes: CheermoteSource = CheermoteSource { emptyList() }

    override suspend fun topLive(cursor: String?): Outcome<Page<Stream>> = api.topLive(sessions, cursor)

    override suspend fun categories(cursor: String?): Outcome<Page<Category>> = api.categories(sessions, cursor)

    override suspend fun liveInCategory(
        categoryId: String,
        cursor: String?,
    ): Outcome<Page<Stream>> = api.categoryLive(sessions, categoryId, cursor)

    override suspend fun search(query: String): Outcome<SearchResults> = api.search(query)

    override suspend fun channel(ref: ChannelRef): Outcome<Channel> = api.channel(sessions, ref)

    override suspend fun followed(): Outcome<Followed> = api.followed(sessions)

    override suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Outcome<Unit> = api.setFollow(sessions, channel.ref, follow)

    /**
     * Followed and searched channels come without their IDs, so a blank [Channel.id] or [Channel.chatId] is resolved
     * first. A failed resolve is thrown as [LurkException]. For Kick the chat channel's `channelId` is the slug,
     * which is what the emote and badge sources take.
     */
    override suspend fun chat(channel: Channel): ChatEndpoints {
        val resolved = if (channel.id.isBlank() || channel.chatId.isBlank()) resolve(channel.ref) else channel
        val slug = resolved.ref.login
        val token: suspend () -> String? = { sessions.token(Platform.KICK) }
        val sender =
            if (token() == null) null else KickSender(api, resolved.chatId, token, { null }, clock)
        return ChatEndpoints(
            channel = ChatChannel(Platform.KICK, slug, slug),
            transport = CentrifugoTransport(bootstrap, connector, resolved.id, resolved.chatId, sessions),
            history = KickHistorySource(api, resolved.id),
            sender = sender,
        )
    }

    private suspend fun resolve(ref: ChannelRef): Channel =
        when (val result = api.channel(sessions, ref)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> throw LurkException(result.error)
        }
}

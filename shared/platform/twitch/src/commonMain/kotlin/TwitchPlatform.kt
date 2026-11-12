package io.github.kemurixd.lurk.platform.twitch

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.chat.ChatChannel
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.RemoteConfig
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
import io.github.kemurixd.lurk.platform.twitch.account.followed
import io.github.kemurixd.lurk.platform.twitch.assets.TwitchBadgeSource
import io.github.kemurixd.lurk.platform.twitch.assets.TwitchCheermoteSource
import io.github.kemurixd.lurk.platform.twitch.assets.TwitchEmoteSource
import io.github.kemurixd.lurk.platform.twitch.chat.IrcSender
import io.github.kemurixd.lurk.platform.twitch.chat.IrcTransport
import io.github.kemurixd.lurk.platform.twitch.chat.RecentMessagesSource
import io.github.kemurixd.lurk.platform.twitch.discovery.categories
import io.github.kemurixd.lurk.platform.twitch.discovery.channel
import io.github.kemurixd.lurk.platform.twitch.discovery.liveInCategory
import io.github.kemurixd.lurk.platform.twitch.discovery.search
import io.github.kemurixd.lurk.platform.twitch.discovery.topLive
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import io.github.kemurixd.lurk.playback.PlaybackSource
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Twitch behind [StreamingPlatform]. */
@Suppress("LongParameterList")
class TwitchPlatform(
    private val gql: GqlClient,
    private val sessions: SessionRepository,
    private val http: HttpClient,
    private val connector: WebSocketConnector,
    private val config: StateFlow<RemoteConfig>,
    private val scope: CoroutineScope,
    override val playback: PlaybackSource,
) : StreamingPlatform {
    override val id: Platform = Platform.TWITCH
    override val capabilities =
        Capabilities(
            follows = true,
            followMutation = false,
            chatSend = true,
            chatHistory = true,
            cheermotes = true,
        )
    override val nativeEmotes: NativeEmoteSource = TwitchEmoteSource(gql)
    override val badges: BadgeSource = TwitchBadgeSource(gql)
    override val cheermotes: CheermoteSource = TwitchCheermoteSource(gql)

    override suspend fun topLive(cursor: String?): Outcome<Page<Stream>> = gql.topLive(sessions, cursor)

    override suspend fun categories(cursor: String?): Outcome<Page<Category>> = gql.categories(sessions, cursor)

    override suspend fun liveInCategory(
        categoryId: String,
        cursor: String?,
    ): Outcome<Page<Stream>> = gql.liveInCategory(sessions, categoryId, cursor)

    override suspend fun search(query: String): Outcome<SearchResults> = gql.search(sessions, query)

    override suspend fun channel(ref: ChannelRef): Outcome<Channel> = gql.channel(sessions, ref)

    override suspend fun followed(): Outcome<Followed> = gql.followed(sessions)

    /** Twitch's web API has no follow mutation we can call. */
    override suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Outcome<Unit> = Outcome.Failure(LurkError.Unsupported)

    /** The IRC login is [Channel.chatId]. The sender exists only while signed in at the time of the call. */
    override suspend fun chat(channel: Channel): ChatEndpoints {
        val login = channel.chatId
        val token: suspend () -> String? = { sessions.token(Platform.TWITCH) }
        val sender =
            if (token() == null) null else IrcSender(connector, config, login, token, scope)
        return ChatEndpoints(
            channel = ChatChannel(Platform.TWITCH, channel.id, channel.ref.login),
            transport = IrcTransport(connector, config, login, token),
            history = RecentMessagesSource(http, config, login),
            sender = sender,
        )
    }
}

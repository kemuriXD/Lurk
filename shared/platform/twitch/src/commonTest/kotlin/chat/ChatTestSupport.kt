package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow

internal const val TEST_IRC_URL = "wss://irc.example/ws"

internal val chatConfig =
    MutableStateFlow(
        RemoteConfig(
            schemaVersion = SUPPORTED_SCHEMA,
            twitch =
                TwitchConfig(
                    gqlClientId = "web-client-id",
                    persistedQueries = emptyMap(),
                    adCueClasses = emptyList(),
                    stitchedAdClasses = emptyList(),
                    recentMessagesUrl = "https://recent.example/api/{channel}?limit=50",
                    ircUrl = TEST_IRC_URL,
                ),
            kick =
                KickConfig(
                    apiBase = "https://kick.example/",
                    webApiBase = "https://kick.example/web/",
                    realtimeConnectionPath = "a",
                    realtimeAuthPath = "b",
                    publicChannelTemplates = emptyList(),
                ),
            emotes = EmoteConfig(bttvZeroWidth = emptyList()),
        ),
    )

internal class FakeIrcConnection : WebSocketConnection {
    val inbound = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var closed = false
    var failSends = false

    override val incoming: Flow<String> = inbound.consumeAsFlow()

    override suspend fun send(text: String) {
        if (closed || failSends) throw LurkException(LurkError.Offline)
        sent += text
    }

    override suspend fun close() {
        closed = true
        inbound.close()
    }
}

internal class FakeIrcConnector(
    private val connectionFactory: () -> FakeIrcConnection = { FakeIrcConnection() },
) : WebSocketConnector {
    val connections = mutableListOf<FakeIrcConnection>()
    val urls = mutableListOf<String>()

    override suspend fun connect(
        url: String,
        headers: Map<String, String>,
    ): WebSocketConnection {
        urls += url
        return connectionFactory().also { connections += it }
    }
}

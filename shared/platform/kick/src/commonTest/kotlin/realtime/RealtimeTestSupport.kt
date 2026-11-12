package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.api.rigConfig
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.content.TextContent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope

internal const val CHANNEL_ID = "1000001"
internal const val CHATROOM_ID = "1000100"
internal const val FIXTURE_TOKEN = "eyJ0.e30.sig"
internal const val SOCKET_URL = "wss://realtime.example/connection/websocket"

internal const val CONNECTION_PATH = "/api/v1/realtime/connection"
internal const val CHAT_CONNECTION_PATH = "/api/v1/realtime/channels/$CHANNEL_ID/chat/connection"
internal const val AUTH_PATH = "/api/v1/realtime/auth/connection"

internal val realtimeConfig =
    MutableStateFlow(
        rigConfig.value.copy(
            kick =
                rigConfig.value.kick.copy(
                    realtimeConnectionPath = "realtime/connection",
                    realtimeAuthPath = "realtime/auth/connection",
                    publicChannelTemplates =
                        listOf(
                            "chatroom_{chatroomId}",
                            "channel_{channelId}",
                            "chatrooms.{chatroomId}",
                            "chatrooms.{chatroomId}.v2",
                            "channel.{channelId}",
                        ),
                ),
        ),
    )

internal fun HttpRequestData.bodyText(): String? = (body as? TextContent)?.text

/** Answers the three bootstrap POSTs with the recorded fixtures; [auth] may vary the token per call. */
internal fun MockRequestHandleScope.recorded(
    request: HttpRequestData,
    auth: () -> String = { fixture("realtime/realtime-auth.json") },
): HttpResponseData =
    when (request.url.encodedPath) {
        CONNECTION_PATH -> json(fixture("realtime/realtime-connection.json"))
        CHAT_CONNECTION_PATH -> json(fixture("realtime/realtime-chat-connection.json"))
        AUTH_PATH -> json(auth())
        else -> error("unexpected request to ${request.url.encodedPath}")
    }

/** A bootstrap over a mock engine that records every request into [seen]. */
internal fun TestScope.bootstrapRig(
    seen: MutableList<HttpRequestData>,
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { recorded(it) },
): RealtimeBootstrap =
    RealtimeBootstrap(
        Rig(
            mockEngine {
                seen += it
                handler(it)
            },
        ).api,
        realtimeConfig,
    )

internal class FakeSocket : WebSocketConnection {
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

internal class FakeSocketConnector(
    private val socketFactory: () -> FakeSocket = { FakeSocket() },
) : WebSocketConnector {
    val sockets = mutableListOf<FakeSocket>()
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun connect(
        url: String,
        headers: Map<String, String>,
    ): WebSocketConnection {
        urls += url
        this.headers += headers
        return socketFactory().also { sockets += it }
    }
}

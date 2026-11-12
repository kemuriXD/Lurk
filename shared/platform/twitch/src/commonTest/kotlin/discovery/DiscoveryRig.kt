package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.platform.twitch.gql.DeviceId
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A GQL client over a mock engine, with a session repository, for the discovery tests. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiscoveryRig(
    val gql: GqlClient,
    val sessions: SessionRepository,
    val seen: List<HttpRequestData>,
) {
    /** The single operation object of the n-th request. */
    fun operation(index: Int = 0): JsonObject =
        Json
            .parseToJsonElement((seen[index].body as TextContent).text)
            .jsonArray
            .single()
            .jsonObject
}

private class MemoryStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() = values.clear()
}

private class NoProfiles : ProfileFetcher {
    override val platform = Platform.TWITCH

    override suspend fun profile(token: String): Outcome<Account> = Outcome.Failure(LurkError.AuthExpired)
}

private class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
}

private val rigConfig =
    MutableStateFlow(
        RemoteConfig(
            schemaVersion = SUPPORTED_SCHEMA,
            twitch =
                TwitchConfig(
                    gqlClientId = "web-client-id",
                    persistedQueries = emptyMap(),
                    adCueClasses = emptyList(),
                    stitchedAdClasses = emptyList(),
                    recentMessagesUrl = "https://recent.example/",
                    gqlUrl = "https://gql.example/gql",
                    usherBase = "https://usher.example",
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

internal fun MockRequestHandleScope.reply(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** Signed out, so each call reaches the mock engine without a token. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.discoveryRig(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): DiscoveryRig {
    val sessions = SessionRepository(MemoryStore(), listOf(NoProfiles()), backgroundScope)
    val seen = mutableListOf<HttpRequestData>()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val engine =
        MockEngine(
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    seen += request
                    handler(request)
                }
            },
        )
    val gql = GqlClient(createHttpClient(engine), rigConfig, DeviceId(MemoryStore()), TestDispatchers(dispatcher), null)
    return DiscoveryRig(gql, sessions, seen)
}

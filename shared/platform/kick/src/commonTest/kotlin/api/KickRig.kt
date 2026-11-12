package io.github.kemurixd.lurk.platform.kick.api

import io.github.kemurixd.lurk.core.config.EmoteConfig
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.config.SUPPORTED_SCHEMA
import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

internal val rigConfig =
    MutableStateFlow(
        RemoteConfig(
            schemaVersion = SUPPORTED_SCHEMA,
            twitch =
                TwitchConfig(
                    gqlClientId = "c",
                    persistedQueries = emptyMap(),
                    adCueClasses = emptyList(),
                    stitchedAdClasses = emptyList(),
                    recentMessagesUrl = "https://recent.example/",
                ),
            kick =
                KickConfig(
                    apiBase = "https://kick.example/api/",
                    webApiBase = "https://web.kick.example/api/v1/",
                    searchApiBase = "https://search.kick.example/api/v1/",
                    realtimeConnectionPath = "a",
                    realtimeAuthPath = "b",
                    publicChannelTemplates = emptyList(),
                ),
            emotes = EmoteConfig(bttvZeroWidth = emptyList()),
        ),
    )

internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.cloudflareBlock(html: String): HttpResponseData =
    respond(html, HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "text/html"))

/** A MockEngine on the test scheduler, so parallel calls interleave in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.mockEngine(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        },
    )

internal class Rig(
    engine: MockEngine,
    solver: ChallengeSolver? = null,
    val cookies: AcceptAllCookiesStorage = AcceptAllCookiesStorage(),
) {
    val gate = CloudflareGate(solver, cookies)
    val api = KickApi(createHttpClient(engine, cookies), rigConfig, gate)
}

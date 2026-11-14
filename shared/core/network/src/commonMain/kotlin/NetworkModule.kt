package io.github.kemurixd.lurk.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import org.koin.dsl.module
import kotlin.time.TimeSource

/**
 * The [CookiesStorage] is bound on its own so the client and anything that writes cookies for it (a solved
 * Cloudflare challenge) share one jar. The platform binds the [NetworkStatus] the client consults.
 */
val networkModule =
    module {
        single<CookiesStorage> { AcceptAllCookiesStorage() }
        single<HttpClient> { createHttpClient(cookies = get(), network = get()) }
        single<WebSocketConnector> { KtorWebSocketConnector(get()) }
        single { Backoff() }
        single { HostRateLimiter(timeSource = TimeSource.Monotonic) }
    }

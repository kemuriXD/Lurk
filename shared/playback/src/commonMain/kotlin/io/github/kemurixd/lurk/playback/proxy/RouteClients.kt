package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.network.EngineProxy
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.network.proxiedEngine
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Where a playlist request goes. */
sealed interface Route {
    data object Direct : Route

    data class ViaProxy(
        val endpoint: ProxyEndpoint,
    ) : Route
}

/** Hands out the HTTP client for a [Route]. */
interface RouteClients {
    fun clientFor(route: Route): HttpClient
}

private const val PROXY_REQUEST_TIMEOUT_MS = 10_000L

/**
 * Builds one client per proxy endpoint, built on [createHttpClient] so its error mapping applies.
 * [Route.Direct] and URL templates (a rewrite, not a proxy) use [direct]. Proxied clients time out after
 * [requestTimeoutMillis], so a dead proxy fails fast. [close] closes the clients it built, not [direct].
 */
@OptIn(ExperimentalAtomicApi::class)
class DefaultRouteClients(
    private val direct: HttpClient,
    private val requestTimeoutMillis: Long = PROXY_REQUEST_TIMEOUT_MS,
    private val engineFor: (EngineProxy) -> HttpClientEngine = ::proxiedEngine,
) : RouteClients {
    private val proxied = AtomicReference<Map<ProxyEndpoint, HttpClient>>(emptyMap())

    override fun clientFor(route: Route): HttpClient =
        when (route) {
            Route.Direct -> direct
            is Route.ViaProxy -> proxiedClient(route.endpoint) ?: direct
        }

    private fun proxiedClient(endpoint: ProxyEndpoint): HttpClient? =
        endpoint.toEngineProxy()?.let { cached(endpoint, it) }

    private fun cached(
        endpoint: ProxyEndpoint,
        engineProxy: EngineProxy,
    ): HttpClient {
        while (true) {
            val current = proxied.load()
            val existing = current[endpoint]
            if (existing != null) return existing
            val created =
                createHttpClient(engine = engineFor(engineProxy), requestTimeoutMs = requestTimeoutMillis)
            if (proxied.compareAndSet(current, current + (endpoint to created))) return created
            created.close()
        }
    }

    fun close() {
        proxied.exchange(emptyMap()).values.forEach(HttpClient::close)
    }
}

private fun ProxyEndpoint.toEngineProxy(): EngineProxy? =
    when (this) {
        is ProxyEndpoint.UrlTemplate -> null
        is ProxyEndpoint.Http -> EngineProxy.Http(host, port, credentials?.username, credentials?.password)
        is ProxyEndpoint.Socks5 -> EngineProxy.Socks5(host, port, credentials?.username, credentials?.password)
    }

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

private const val PROXY_REQUEST_TIMEOUT_MS = 5_000L

/**
 * Builds one client per proxy endpoint, built on [createHttpClient] so its error mapping applies.
 * [Route.Direct] and URL templates (a rewrite, not a proxy) use [direct]. Proxied clients time out after
 * [requestTimeoutMillis] (5 s by default), so a dead proxy fails fast. [close] closes the clients it built, not [direct].
 */
@OptIn(ExperimentalAtomicApi::class)
class DefaultRouteClients(
    private val direct: HttpClient,
    private val engineFor: (EngineProxy.Http) -> HttpClientEngine = ::proxiedEngine,
    private val requestTimeoutMillis: Long = PROXY_REQUEST_TIMEOUT_MS,
) : RouteClients {
    private val proxied = AtomicReference<Map<ProxyEndpoint, Entry>>(emptyMap())

    override fun clientFor(route: Route): HttpClient =
        when (route) {
            Route.Direct -> direct
            is Route.ViaProxy -> proxiedClient(route.endpoint) ?: direct
        }

    private fun proxiedClient(endpoint: ProxyEndpoint): HttpClient? =
        endpoint.toEngineProxy()?.let { cached(endpoint, it) }

    private fun cached(
        endpoint: ProxyEndpoint,
        engineProxy: EngineProxy.Http,
    ): HttpClient {
        while (true) {
            val current = proxied.load()
            val existing = current[endpoint]
            if (existing != null) return existing.client
            val engine = engineFor(engineProxy)
            val created = Entry(engine, createHttpClient(engine = engine, requestTimeoutMs = requestTimeoutMillis))
            if (proxied.compareAndSet(current, current + (endpoint to created))) return created.client
            created.close()
        }
    }

    /** The client does not own its engine, so both are closed together. */
    private class Entry(
        val engine: HttpClientEngine,
        val client: HttpClient,
    ) {
        fun close() {
            client.close()
            engine.close()
        }
    }

    fun close() {
        proxied.exchange(emptyMap()).values.forEach(Entry::close)
    }
}

private fun ProxyEndpoint.toEngineProxy(): EngineProxy.Http? =
    when (this) {
        is ProxyEndpoint.UrlTemplate -> null
        is ProxyEndpoint.Http -> EngineProxy.Http(host, port, credentials?.username, credentials?.password)
    }

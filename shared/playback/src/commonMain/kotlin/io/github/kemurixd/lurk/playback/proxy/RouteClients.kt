package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.network.EngineProxy
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.network.proxiedEngine
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Where a playlist request goes. */
internal sealed interface Route {
    data object Direct : Route

    data class ViaProxy(
        val endpoint: ProxyEndpoint,
    ) : Route
}

/** Hands out the HTTP client for a [Route]. */
internal interface RouteClients {
    fun clientFor(route: Route): HttpClient
}

/** How long a proxied request, or one to a URL template's host, may take. */
internal const val PROXY_REQUEST_TIMEOUT_MS = 5_000L

/**
 * A plain GET over [route]'s client; the body comes back exactly as served. A request to a URL template's host
 * (its master over [Route.ViaProxy], or [toTemplateHost]) times out after [PROXY_REQUEST_TIMEOUT_MS] like a proxied
 * one, though it uses the direct client. Unexpected exceptions are rethrown, as [outcomeOf] does.
 */
internal suspend fun RouteClients.getText(
    route: Route,
    url: String,
    headers: Map<String, String>,
    toTemplateHost: Boolean = false,
): Outcome<String> {
    val shortTimeout = toTemplateHost || (route as? Route.ViaProxy)?.endpoint is ProxyEndpoint.UrlTemplate
    return outcomeOf {
        clientFor(route)
            .get(url) {
                headers.forEach { (name, value) -> header(name, value) }
                if (shortTimeout) timeout { requestTimeoutMillis = PROXY_REQUEST_TIMEOUT_MS }
            }.bodyAsText()
    }
}

/**
 * Builds one client per proxy endpoint, built on [createHttpClient] so its error mapping applies.
 * [Route.Direct] and URL templates (a rewrite, not a proxy) use [direct]. Proxied clients time out after
 * [requestTimeoutMillis] (5 s by default), so a dead proxy fails fast. [close] closes the clients it built, not
 * [direct].
 */
@OptIn(ExperimentalAtomicApi::class)
internal class DefaultRouteClients(
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

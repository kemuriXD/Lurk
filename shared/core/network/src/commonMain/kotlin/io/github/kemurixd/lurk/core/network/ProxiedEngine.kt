package io.github.kemurixd.lurk.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * An engine whose connections all go through [proxy]. Pair it with [createHttpClient] so transport errors
 * map to [io.github.kemurixd.lurk.core.common.LurkException].
 *
 * SOCKS5 with credentials is not supported: the JVM only offers SOCKS username and password through the
 * process-wide authenticator, which would hand one proxy's credentials to every connection. Such an engine
 * fails each request with [io.github.kemurixd.lurk.core.common.LurkError.ProxyFailed].
 */
expect fun proxiedEngine(proxy: EngineProxy): HttpClientEngine

internal fun EngineProxy.needsUnsupportedSocksAuth(): Boolean =
    this is EngineProxy.Socks5 && (username != null || password != null)

package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI

/** An engine that fails every request with [LurkError.ProxyFailed]. */
internal fun proxyFailedEngine(): HttpClientEngine = ProxyFailedEngine()

private class ProxyFailedEngine : HttpClientEngineBase("lurk-proxy-failed") {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val supportedCapabilities =
        setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability, WebSocketCapability)

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData = throw LurkException(LurkError.ProxyFailed)
}

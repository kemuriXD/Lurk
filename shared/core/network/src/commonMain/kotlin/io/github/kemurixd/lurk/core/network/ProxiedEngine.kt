package io.github.kemurixd.lurk.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * An engine whose connections all go through [proxy]. Pair it with [createHttpClient] so transport errors
 * map to [io.github.kemurixd.lurk.core.common.LurkException].
 */
expect fun proxiedEngine(proxy: EngineProxy.Http): HttpClientEngine

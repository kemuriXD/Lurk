package io.github.kemurixd.lurk.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

actual fun proxiedEngine(proxy: EngineProxy): HttpClientEngine =
    if (proxy.needsUnsupportedSocksAuth()) {
        proxyFailedEngine()
    } else {
        val okHttp = proxiedOkHttpClient(proxy)
        shuttingDownEngine(OkHttp.create { preconfigured = okHttp }, okHttp)
    }

/** Ktor does not own a preconfigured client, so closing the engine also releases its threads and pool. */
internal fun shuttingDownEngine(
    delegate: HttpClientEngine,
    okHttp: OkHttpClient,
): HttpClientEngine =
    object : HttpClientEngine by delegate {
        override fun close() {
            delegate.close()
            okHttp.dispatcher.executorService.shutdown()
            okHttp.connectionPool.evictAll()
        }
    }

// Identical to ProxiedEngine.android.kt; keep the two files in step.
internal fun proxiedOkHttpClient(proxy: EngineProxy): OkHttpClient {
    val builder = OkHttpClient.Builder()
    when (proxy) {
        is EngineProxy.Http -> {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)))
            val user = proxy.username
            if (user != null || proxy.password != null) {
                val credential = Credentials.basic(user.orEmpty(), proxy.password.orEmpty(), Charsets.UTF_8)
                builder.proxyAuthenticator(
                    Authenticator { _, response ->
                        if (response.request.header("Proxy-Authorization") != null) {
                            null
                        } else {
                            response.request
                                .newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    },
                )
            }
        }

        is EngineProxy.Socks5 -> {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(proxy.host, proxy.port)))
        }
    }
    return builder.build()
}

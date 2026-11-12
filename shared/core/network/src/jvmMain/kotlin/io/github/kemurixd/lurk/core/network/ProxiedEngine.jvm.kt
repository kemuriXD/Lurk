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
        OkHttp.create { preconfigured = proxiedOkHttpClient(proxy) }
    }

internal fun proxiedOkHttpClient(proxy: EngineProxy): OkHttpClient {
    val builder = OkHttpClient.Builder()
    when (proxy) {
        is EngineProxy.Http -> {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)))
            val user = proxy.username
            if (user != null) {
                val credential = Credentials.basic(user, proxy.password.orEmpty())
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

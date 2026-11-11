package io.github.kemurixd.lurk.core.network

import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame

class NetworkModuleTest {
    @Test
    fun providesSingletons() {
        val koin = koinApplication { modules(networkModule) }.koin
        assertSame(koin.get<HttpClient>(), koin.get<HttpClient>())
        assertSame(koin.get<Backoff>(), koin.get<Backoff>())
        assertSame(koin.get<HostRateLimiter>(), koin.get<HostRateLimiter>())
        koin.get<HttpClient>().close()
    }
}

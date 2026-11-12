package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.network.EngineProxy
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.playback.ProxyCredentials
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RouteClientsTest {
    private val direct = createHttpClient(MockEngine { respondOk() })
    private val seen = mutableListOf<EngineProxy>()

    private val engines = mutableListOf<RecordingEngine>()

    private class RecordingEngine(
        private val delegate: HttpClientEngine,
    ) : HttpClientEngine by delegate {
        var closeCount = 0

        override fun close() {
            closeCount++
            delegate.close()
        }
    }

    private fun engineFactory(handler: suspend () -> Unit): (EngineProxy) -> HttpClientEngine =
        { proxy ->
            seen += proxy
            RecordingEngine(
                MockEngine {
                    handler()
                    respondOk()
                },
            ).also { engines += it }
        }

    private fun clients(
        timeoutMillis: Long,
        handler: suspend () -> Unit = {},
    ) = DefaultRouteClients(direct, engineFactory(handler), timeoutMillis)

    private fun clients(handler: suspend () -> Unit = {}) = DefaultRouteClients(direct, engineFactory(handler))

    @Test
    fun closeClosesProxiedEnginesButNotDirect() {
        val clients = clients()
        clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 1)))
        clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 2)))
        clients.close()
        assertEquals(listOf(1, 1), engines.map { it.closeCount })
        clients.close()
        assertEquals(listOf(1, 1), engines.map { it.closeCount })
    }

    @Test
    fun directAndUrlTemplateUseTheDirectClient() {
        val clients = clients()
        assertSame(direct, clients.clientFor(Route.Direct))
        val template = ProxyEndpoint.UrlTemplate("https://p.example/{channel}.m3u8")
        assertSame(direct, clients.clientFor(Route.ViaProxy(template)))
        assertEquals(emptyList(), seen)
    }

    @Test
    fun sameEndpointReturnsSameClient() {
        val clients = clients()
        val a = clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 8080)))
        val b = clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 8080)))
        val c = clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 8081)))
        assertSame(a, b)
        assertNotSame(a, c)
        assertEquals(2, seen.size)
    }

    @Test
    fun endpointsMapToEngineProxies() {
        val clients = clients()
        clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 1, ProxyCredentials("u", "p"))))
        clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 2)))
        assertEquals(EngineProxy.Http("127.0.0.1", 1, "u", "p"), seen[0])
        assertEquals(EngineProxy.Http("127.0.0.1", 2, null, null), seen[1])
    }

    @Test
    fun proxiedClientsTimeOutFast() =
        runTest {
            val clients = clients(timeoutMillis = 50) { delay(5_000) }
            val client = clients.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 2)))
            val failure = assertFailsWith<LurkException> { client.get("https://target.example/x.m3u8") }
            assertEquals(LurkError.Timeout, failure.error)
        }

    @Test
    fun defaultProxyTimeoutIsFiveSeconds() =
        runTest {
            val slow = clients { delay(7_000) }.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 3)))
            val failure = assertFailsWith<LurkException> { slow.get("https://target.example/x.m3u8") }
            assertEquals(LurkError.Timeout, failure.error)
            val ok = clients { delay(3_000) }.clientFor(Route.ViaProxy(ProxyEndpoint.Http("127.0.0.1", 4)))
            ok.get("https://target.example/x.m3u8")
        }
}

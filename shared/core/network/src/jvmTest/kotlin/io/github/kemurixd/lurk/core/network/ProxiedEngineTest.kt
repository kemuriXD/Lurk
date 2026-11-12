package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxiedEngineTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun http(
        username: String? = null,
        password: String? = null,
    ): EngineProxy.Http {
        server.start()
        return EngineProxy.Http("127.0.0.1", server.port, username, password)
    }

    private fun challenge() =
        MockResponse
            .Builder()
            .code(407)
            .addHeader("Proxy-Authenticate", "Basic realm=\"p\"")
            .build()

    @Test
    fun httpProxyCarriesRequest() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("#EXTM3U")
                    .build(),
            )
            val client = createHttpClient(engine = proxiedEngine(http()))
            val body = client.get("http://target.example/x.m3u8").bodyAsText()
            client.close()
            assertEquals("#EXTM3U", body)
            assertEquals("GET http://target.example/x.m3u8 HTTP/1.1", server.takeRequest().requestLine)
        }

    @Test
    fun httpProxyAuthAfter407() =
        runBlocking {
            server.enqueue(challenge())
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("ok")
                    .build(),
            )
            val client = createHttpClient(engine = proxiedEngine(http("user1", "hunter2")))
            assertEquals("ok", client.get("http://target.example/x.m3u8").bodyAsText())
            client.close()
            assertNull(server.takeRequest().headers["Proxy-Authorization"])
            assertEquals("Basic dXNlcjE6aHVudGVyMg==", server.takeRequest().headers["Proxy-Authorization"])
        }

    @Test
    fun httpProxyGivesUpAfterSecond407() =
        runBlocking {
            repeat(3) { server.enqueue(challenge()) }
            val client = createHttpClient(engine = proxiedEngine(http("user1", "wrong")))
            val failure = assertFailsWith<LurkException> { client.get("http://target.example/x.m3u8") }
            client.close()
            assertEquals(LurkError.Http(407), failure.error)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun socksUsesRemoteDns() {
        val proxy = proxiedOkHttpClient(EngineProxy.Socks5("proxy.example", 1080, null, null)).proxy
        assertEquals(Proxy.Type.SOCKS, proxy?.type())
        assertTrue((proxy?.address() as InetSocketAddress).isUnresolved)
    }

    @Test
    fun socksWithCredentialsFailsEveryRequest() =
        runBlocking {
            val client = createHttpClient(engine = proxiedEngine(EngineProxy.Socks5("127.0.0.1", 1, "u", "p")))
            val failure = assertFailsWith<LurkException> { client.get("https://target.example/x.m3u8") }
            client.close()
            assertEquals(LurkError.ProxyFailed, failure.error)
        }

    @Test
    fun engineToStringHidesSecrets() {
        val http = EngineProxy.Http("proxy.example", 8080, "user1", "hunter2")
        val socks = EngineProxy.Socks5("proxy.example", 1080, "user1", "hunter2")
        for (text in listOf(http.toString(), socks.toString())) {
            assertFalse("user1" in text)
            assertFalse("hunter2" in text)
            assertTrue("proxy.example" in text)
        }
    }

    @Test
    fun httpProxyAuthEncodesNonAsciiCredentialsAsUtf8() =
        runBlocking {
            server.enqueue(challenge())
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("ok")
                    .build(),
            )
            val client = createHttpClient(engine = proxiedEngine(http("us\u00e9r", "p\u00e4ss")))
            client.get("http://target.example/x.m3u8")
            client.close()
            server.takeRequest()
            val expected =
                "Basic " +
                    java.util.Base64
                        .getEncoder()
                        .encodeToString("us\u00e9r:p\u00e4ss".toByteArray(Charsets.UTF_8))
            assertEquals(expected, server.takeRequest().headers["Proxy-Authorization"])
        }

    @Test
    fun socksPasswordWithoutUsernameAlsoFails() =
        runBlocking {
            val client = createHttpClient(engine = proxiedEngine(EngineProxy.Socks5("127.0.0.1", 1, null, "p")))
            val failure = assertFailsWith<LurkException> { client.get("https://target.example/x.m3u8") }
            client.close()
            assertEquals(LurkError.ProxyFailed, failure.error)
        }

    @Test
    fun closingTheEngineShutsDownTheOkHttpClient() {
        val okHttp = proxiedOkHttpClient(EngineProxy.Http("127.0.0.1", 1))
        shuttingDownEngine(
            io.ktor.client.engine.okhttp.OkHttp
                .create { preconfigured = okHttp },
            okHttp,
        ).close()
        assertTrue(okHttp.dispatcher.executorService.isShutdown)
    }
}

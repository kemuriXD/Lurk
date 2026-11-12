package io.github.kemurixd.lurk.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyEndpointTest {
    private fun valid(input: String): ProxyEndpoint = (ProxyEndpoint.parse(input) as ProxyParseResult.Valid).endpoint

    private fun invalid(input: String): ProxyParseError = (ProxyEndpoint.parse(input) as ProxyParseResult.Invalid).error

    @Test
    fun parsesHttpProxyWithDecodedCredentials() {
        assertEquals(
            ProxyEndpoint.Http("proxy.example", 8080, ProxyCredentials("u", "p@ss")),
            valid("http://u:p%40ss@proxy.example:8080"),
        )
    }

    @Test
    fun parsesSocks5hWithoutCredentials() {
        assertEquals(ProxyEndpoint.Socks5("proxy.example", 1080, null), valid("socks5h://proxy.example:1080"))
        assertEquals(ProxyEndpoint.Socks5("proxy.example", 1080, null), valid("socks5://proxy.example:1080"))
    }

    @Test
    fun parsesTemplates() {
        val https = valid("https://lol.example/playlist/{channel}.m3u8")
        assertEquals(ProxyEndpoint.UrlTemplate("https://lol.example/playlist/{channel}.m3u8"), https)
        assertEquals("lol.example", https.label)
        assertTrue(valid("http://lol.example/p/{channel}") is ProxyEndpoint.UrlTemplate)
    }

    @Test
    fun trimsInput() {
        assertEquals(ProxyEndpoint.Http("proxy.example", 8080), valid("  http://proxy.example:8080 "))
    }

    @Test
    fun rejectsBadInput() {
        assertEquals(ProxyParseError.TLS_PROXY_UNSUPPORTED, invalid("https://proxy.example:443"))
        assertEquals(ProxyParseError.UNKNOWN_SCHEME, invalid("ftp://x:1"))
        assertEquals(ProxyParseError.BAD_PORT, invalid("http://proxy.example"))
        assertEquals(ProxyParseError.BAD_PORT, invalid("http://proxy.example:70000"))
        assertEquals(ProxyParseError.BAD_PORT, invalid("http://proxy.example:0"))
        assertEquals(ProxyParseError.BAD_HOST, invalid("http://:8080"))
        assertEquals(ProxyParseError.EMPTY, invalid("   "))
    }

    @Test
    fun labelIsHostAndPort() {
        assertEquals("proxy.example:8080", ProxyEndpoint.Http("proxy.example", 8080).label)
        assertEquals("proxy.example:1080", ProxyEndpoint.Socks5("proxy.example", 1080).label)
    }

    @Test
    fun credentialsNeverInToString() {
        val endpoint = ProxyEndpoint.Http("proxy.example", 8080, ProxyCredentials("user1", "hunter2"))
        val texts =
            listOf(
                endpoint.toString(),
                ProxySettings(ProxyMode.ALWAYS, listOf(endpoint)).toString(),
                ProxyCredentials("user1", "hunter2").toString(),
            )
        texts.forEach {
            assertFalse(it.contains("user1"), it)
            assertFalse(it.contains("hunter2"), it)
        }
        assertEquals("Http(proxy.example:8080)", endpoint.toString())
    }

    @Test
    fun templateToStringShowsHostOnly() {
        assertEquals(
            "UrlTemplate(lol.example)",
            ProxyEndpoint.UrlTemplate("https://lol.example/secret-key/{channel}.m3u8").toString(),
        )
    }
}

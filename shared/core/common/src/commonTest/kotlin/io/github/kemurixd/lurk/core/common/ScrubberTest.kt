package io.github.kemurixd.lurk.core.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScrubberTest {
    @Test
    fun scrubsSpikeSecretShapes() {
        val cases =
            mapOf(
                "PASS oauth:abcd1234efgh5678ijkl9012" to "abcd1234efgh5678ijkl9012",
                "Cookie: session_token=kat_123abc456def" to "kat_123abc456def",
                "Authorization: Bearer kat_9f8e7d6c5b4a" to "kat_9f8e7d6c5b4a",
                "https://usher.ttvnw.net/api/v2/channel/hls/x.m3u8?sig=deadbeef&token=%7B%22a%22%7D" to "deadbeef",
                "cf_clearance=Zx81kd02mXqP3" to "Zx81kd02mXqP3",
            )
        cases.forEach { (input, secret) ->
            assertFalse(Scrubber.scrub(input).contains(secret), "leaked $secret from: $input")
        }
    }

    @Test
    fun urlQueryIsDroppedAndPathKept() {
        val out = Scrubber.scrub("GET https://usher.ttvnw.net/api/v2/channel/hls/x.m3u8?sig=deadbeef&token=abc done")
        assertEquals("GET https://usher.ttvnw.net/api/v2/channel/hls/x.m3u8?… done", out)
    }

    @Test
    fun oauthAndBearerKeepTheirPrefix() {
        assertEquals("PASS oauth:…", Scrubber.scrub("PASS oauth:abcd1234efgh5678ijkl9012"))
        assertContains(Scrubber.scrub("sent Bearer kat_9f8e7d6c5b4a to host"), "Bearer …")
    }

    @Test
    fun headerLinesKeepNameAndDropValue() {
        assertEquals("Cookie: …", Scrubber.scrub("Cookie: a=b; c=d"))
        assertEquals("Set-Cookie: …", Scrubber.scrub("Set-Cookie: id=1; Path=/"))
        assertEquals("Authorization: …", Scrubber.scrub("Authorization: Basic Zm9v"))
    }

    @Test
    fun sensitiveKeyValuePairsAreMasked() {
        assertEquals("cf_clearance=…", Scrubber.scrub("cf_clearance=Zx81kd02mXqP3"))
        assertEquals("a=1 xsrf-token=… b=2", Scrubber.scrub("a=1 xsrf-token=q9 b=2"))
    }

    @Test
    fun longMixedRunsKeepFourCharacters() {
        assertEquals("id abcd…", Scrubber.scrub("id abcd1234efgh5678ijkl9012mn"))
    }

    @Test
    fun sixtyFourHexHashIsExempt() {
        val hash = "bbfa83064e90280dce3eaa9de3a18eb6647505546336313241afdf569b6b34b6"
        assertEquals(hash, Scrubber.scrub(hash))
    }

    @Test
    fun plainProseIsUnchanged() {
        assertEquals("bearer stored", Scrubber.scrub("bearer stored"))
        assertEquals("Is this ok? yes", Scrubber.scrub("Is this ok? yes"))
        assertEquals(
            "a_very_long_identifier_without_digits_here",
            Scrubber.scrub("a_very_long_identifier_without_digits_here"),
        )
    }

    @Test
    fun masksProxyUserinfoForAnyScheme() {
        assertEquals(
            "via http://…@proxy.example:8080 failed",
            Scrubber.scrub("via http://user1:hunter2@proxy.example:8080 failed"),
        )
        assertEquals(
            "via socks5h://…@proxy.example:1080 failed",
            Scrubber.scrub("via socks5h://user1:hunter2@proxy.example:1080 failed"),
        )
        assertEquals(
            "via socks5://…@proxy.example:1080",
            Scrubber.scrub("via socks5://user1:hunter2@proxy.example:1080"),
        )
    }
}

package io.github.kemurixd.lurk.platform.twitch.playback

import io.github.kemurixd.lurk.playback.TokenFlags
import io.ktor.http.Url
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsherUrlTest {
    private val value = """{"channel":"streamer1","show_ads":true,"user_ip":"192.0.2.1"}"""
    private val token = PlaybackToken(value, "0000", TokenFlags(subscriber = false, turbo = false, showAds = true))

    @Test
    fun usherUrlHasSiteParams() {
        val raw = usherUrl("https://usher.example", "streamer1", token, Random(1))
        val url = Url(raw)

        assertEquals("https", url.protocol.name)
        assertEquals("usher.example", url.host)
        assertEquals("/api/channel/hls/streamer1.m3u8", url.encodedPath)
        val expected =
            mapOf(
                "sig" to "0000",
                "token" to value,
                "allow_source" to "true",
                "allow_audio_only" to "true",
                "fast_bread" to "true",
                "playlist_include_framerate" to "true",
                "player_backend" to "mediaplayer",
            )
        for ((name, want) in expected) assertEquals(want, url.parameters[name], name)
        val p = url.parameters["p"].orEmpty().toInt()
        assertTrue(p in 0..9_999_999, "p=$p")
        assertEquals(expected.keys + "p", url.parameters.names())

        val query = raw.substringAfter('?')
        assertFalse('{' in query || '"' in query || ' ' in query, query)
        assertTrue("token=%7B%22channel%22" in query, query)
    }

    @Test
    fun trailingSlashOnTheBaseIsIgnored() {
        val url = Url(usherUrl("https://usher.example/", "streamer1", token, Random(1)))
        assertEquals("/api/channel/hls/streamer1.m3u8", url.encodedPath)
    }

    @Test
    fun cacheBusterComesFromTheGivenRandom() {
        val first = Url(usherUrl("https://usher.example", "streamer1", token, Random(7))).parameters["p"]
        val again = Url(usherUrl("https://usher.example", "streamer1", token, Random(7))).parameters["p"]
        assertEquals(Random(7).nextInt(10_000_000).toString(), first)
        assertEquals(first, again)
    }
}

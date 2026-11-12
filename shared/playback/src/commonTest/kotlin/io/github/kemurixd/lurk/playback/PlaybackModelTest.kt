package io.github.kemurixd.lurk.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackModelTest {
    @Test
    fun adFreeWhenSubscriberTurboOrNoAds() {
        assertFalse(TokenFlags(subscriber = false, turbo = false, showAds = true).adFree)
        assertTrue(TokenFlags(subscriber = true, turbo = false, showAds = true).adFree)
        assertTrue(TokenFlags(subscriber = false, turbo = true, showAds = true).adFree)
        assertTrue(TokenFlags(subscriber = false, turbo = false, showAds = false).adFree)
    }

    @Test
    fun defaultModeIsAlwaysWhenEndpointsExist() {
        val endpoint = ProxyEndpoint.Http("proxy.example", 8080)
        assertEquals(ProxyMode.ALWAYS, ProxySettings.defaultFor(listOf(endpoint)).mode)
        assertEquals(ProxyMode.OFF, ProxySettings.defaultFor(emptyList()).mode)
    }

    @Test
    fun activeNeedsModeAndEndpoints() {
        val endpoint = ProxyEndpoint.Http("proxy.example", 8080)
        assertFalse(ProxySettings(ProxyMode.ALWAYS, emptyList()).active)
        assertFalse(ProxySettings(ProxyMode.OFF, listOf(endpoint)).active)
        assertTrue(ProxySettings(ProxyMode.WHEN_ADS_DETECTED, listOf(endpoint)).active)
    }

    @Test
    fun prefsDefaults() {
        val prefs = PlaybackPrefs()
        assertEquals(QualityPreference.Auto, prefs.quality)
        assertTrue(prefs.lowLatency)
        assertFalse(prefs.proxy.active)
    }

    @Test
    fun grantToStringHidesHeadersAndQuery() {
        val grant =
            PlaybackGrant(
                masterUrl = "https://usher.example/hls/testchannel.m3u8?sig=sigvalue1&token=tokenvalue1",
                headers = mapOf("Authorization" to "OAuth headervalue1"),
            )
        val text = grant.toString()
        listOf("sigvalue1", "tokenvalue1", "headervalue1", "Authorization").forEach {
            assertFalse(it in text, "$it leaked: $text")
        }
        assertTrue("usher.example/hls/testchannel.m3u8" in text, text)
    }
}

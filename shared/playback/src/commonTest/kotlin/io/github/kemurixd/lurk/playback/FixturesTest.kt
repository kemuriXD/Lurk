package io.github.kemurixd.lurk.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixturesTest {
    private val playlists =
        listOf(
            "twitch/master.m3u8",
            "twitch/media-live.m3u8",
            "twitch/media-cue.m3u8",
            "twitch/media-stitched-preroll.m3u8",
            "twitch/media-stitched-midroll-pod.m3u8",
            "twitch/media-stitched-title-only.m3u8",
            "broken/truncated.m3u8",
        )

    @Test
    fun playlistsStartWithExtm3u() {
        for (path in playlists) {
            assertTrue(fixture(path).startsWith("#EXTM3U"), path)
        }
        for ((name, text) in fixtureDir("twitch/cue-break")) {
            assertTrue(text.startsWith("#EXTM3U"), name)
        }
    }

    @Test
    fun brokenFilesAreBroken() {
        assertEquals("", fixture("broken/empty.m3u8"))
        assertFalse(fixture("broken/html.m3u8").startsWith("#EXTM3U"))
        assertTrue(fixture("broken/html.m3u8").contains("<html"))
        assertFalse(fixture("broken/truncated.m3u8").endsWith("\n"))
    }

    @Test
    fun cueBreakHoldsAFullBreakWithQuietEnds() {
        val refreshes = fixtureDir("twitch/cue-break")
        assertTrue(refreshes.size >= 10, "only ${refreshes.size} refreshes")
        assertFalse(refreshes.first().second.contains("twitch-maf-ad"))
        assertFalse(refreshes.last().second.contains("twitch-maf-ad"))
        assertTrue(refreshes.any { it.second.contains("twitch-maf-ad") })
        assertEquals(refreshes.map { it.first }.sorted(), refreshes.map { it.first })
    }

    @Test
    fun fixturesShowTheMarkersTheyAreNamedFor() {
        assertFalse(fixture("twitch/media-live.m3u8").contains("twitch-maf-ad"))
        assertTrue(fixture("twitch/media-cue.m3u8").contains("twitch-maf-ad"))
        assertTrue(fixture("twitch/media-stitched-preroll.m3u8").contains("X-TV-TWITCH-AD-ROLL-TYPE=\"PREROLL\""))
        val pod = fixture("twitch/media-stitched-midroll-pod.m3u8")
        assertTrue(pod.contains("X-TV-TWITCH-AD-ROLL-TYPE=\"MIDROLL\""))
        assertTrue(pod.contains("X-TV-TWITCH-AD-POD-LENGTH=\"2\""))
        val titleOnly = fixture("twitch/media-stitched-title-only.m3u8")
        assertEquals(3, titleOnly.lines().count { it.startsWith("#EXTINF") && it.endsWith("Amazon|0000000000") })
        assertFalse(titleOnly.contains("twitch-stitched-ad"))
    }

    @Test
    fun masterKeepsEveryVariant() {
        val master = fixture("twitch/master.m3u8")
        assertEquals(6, master.lines().count { it.startsWith("#EXT-X-STREAM-INF") })
        assertTrue(master.contains("STABLE-VARIANT-ID=\"1080p60\""))
        assertTrue(master.contains("STABLE-VARIANT-ID=\"160p30\""))
    }

    @Test
    fun fixturesCarryNoQueryStringsOrHosts() {
        val all =
            playlists.map { it to fixture(it) } +
                fixtureDir("twitch/cue-break").map { it.first to it.second }
        for ((name, text) in all) {
            assertFalse(text.contains("?"), name)
            assertFalse(text.contains("ttvnw.net"), name)
            assertFalse(text.contains("cloudfront"), name)
        }
    }
}

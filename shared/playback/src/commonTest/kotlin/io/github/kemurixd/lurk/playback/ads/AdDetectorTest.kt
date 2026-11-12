package io.github.kemurixd.lurk.playback.ads

import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.fixture
import io.github.kemurixd.lurk.playback.fixtureDir
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AdDetectorTest {
    private val defaults = AdMarkers(listOf("twitch-maf-ad"), listOf("twitch-stitched-ad"))

    private fun inspect(
        name: String,
        markers: AdMarkers = defaults,
    ): PlaylistAdInfo {
        val playlist = assertNotNull(MediaPlaylist.parse(fixture(name)))
        return AdDetector { markers }.inspect(playlist)
    }

    @Test
    fun liveHasNoAds() {
        val info = inspect("twitch/media-live.m3u8")
        assertTrue(info.cues.isEmpty())
        assertTrue(info.stitched.isEmpty())
        assertFalse(info.hasStitched)
    }

    @Test
    fun cueIsCueOnly() {
        val info = inspect("twitch/media-cue.m3u8")
        val cue = info.cues.single()
        assertEquals(60.seconds, cue.planned)
        assertEquals(Instant.parse("2026-11-07T16:24:06.531Z"), cue.start)
        assertTrue(info.stitched.isEmpty())
    }

    @Test
    fun stitchedPreroll() {
        val info = inspect("twitch/media-stitched-preroll.m3u8")
        val w = info.stitched.single()
        assertEquals(RollType.PREROLL, w.roll)
        assertEquals(1, w.podLength)
        assertEquals(Instant.parse("2026-11-07T22:33:21.117Z"), w.start)
        assertEquals(w.start + 15217.milliseconds, w.end)
        assertTrue(info.hasStitched)
        assertTrue(info.cues.isEmpty())
    }

    @Test
    fun stitchedMidrollPod() {
        val info = inspect("twitch/media-stitched-midroll-pod.m3u8")
        val w = info.stitched.single()
        assertEquals(RollType.MIDROLL, w.roll)
        assertEquals(2, w.podLength)
        assertEquals(Instant.parse("2026-11-07T22:40:04.333Z"), w.start)
        assertEquals(Instant.parse("2026-11-07T22:40:19.550Z") + 15217.milliseconds, w.end)
    }

    @Test
    fun titleOnlyFallback() {
        val info = inspect("twitch/media-stitched-title-only.m3u8")
        val w = info.stitched.single()
        assertEquals(RollType.UNKNOWN, w.roll)
        assertEquals(1, w.podLength)
        assertEquals(6.seconds, w.end - w.start)
    }

    @Test
    fun classesComeFromConfig() {
        val markers = AdMarkers(cueClasses = listOf("x-cue"), stitchedClasses = listOf("x-stitched"))
        val text = fixture("twitch/media-cue.m3u8").replace("CLASS=\"twitch-maf-ad\"", "CLASS=\"x-cue\"")
        val detector = AdDetector { markers }
        assertEquals(1, detector.inspect(assertNotNull(MediaPlaylist.parse(text))).cues.size)
        assertTrue(inspect("twitch/media-cue.m3u8", markers).cues.isEmpty())
    }

    @Test
    fun cueNeverCountsAsStitched() {
        val files = fixtureDir("twitch/cue-break")
        assertTrue(files.isNotEmpty())
        files.forEach { (name, text) ->
            val info = AdDetector { defaults }.inspect(assertNotNull(MediaPlaylist.parse(text)))
            assertFalse(info.hasStitched, name)
        }
        assertTrue(files.any { (_, text) -> text.contains("twitch-maf-ad") })
    }

    @Test
    fun stitchedByClassSubstringAndOtherClassesIgnored() {
        val markers = AdMarkers(listOf("twitch-maf-ad"), listOf("other"))
        val info = inspect("twitch/media-stitched-preroll.m3u8", markers)
        // twitch-stitched-ad is not configured but contains "stitched-ad", and carries X-TV-TWITCH-AD-* attributes.
        assertEquals(1, info.stitched.size)
    }

    @Test
    fun segmentRunWithoutProgramDateTimeIsSkipped() {
        val text = fixture("twitch/media-stitched-title-only.m3u8").replace(Regex("#EXT-X-PROGRAM-DATE-TIME:.*\n"), "")
        val info = AdDetector { defaults }.inspect(assertNotNull(MediaPlaylist.parse(text)))
        assertTrue(info.stitched.isEmpty())
    }
}

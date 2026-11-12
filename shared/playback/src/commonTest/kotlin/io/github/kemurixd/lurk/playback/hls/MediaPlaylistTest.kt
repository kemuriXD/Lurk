package io.github.kemurixd.lurk.playback.hls

import io.github.kemurixd.lurk.playback.fixture
import io.github.kemurixd.lurk.playback.fixtureDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MediaPlaylistTest {
    @Test
    fun mediaFromFixture() {
        val p = assertNotNull(MediaPlaylist.parse(fixture("twitch/media-live.m3u8")))
        assertTrue(p.segments.isNotEmpty())
        assertTrue(p.segments.all { it.duration == 2.seconds && it.title == "live" })
        assertNotNull(p.serverTime)
        assertTrue(p.hasPrefetch)
        assertEquals(2.seconds, p.typicalSegment)
        assertEquals(6.seconds, p.targetDuration)
        assertNotNull(p.segments.first().programDateTime)
    }

    @Test
    fun cueDateRangeParsed() {
        val p = assertNotNull(MediaPlaylist.parse(fixture("twitch/media-cue.m3u8")))
        val cue = p.dateRanges.first { it.klass == "twitch-maf-ad" }
        assertEquals(60.seconds, cue.plannedDuration)
        assertNotNull(cue.start)
        assertNull(cue.duration)
    }

    @Test
    fun stitchedDurationParsed() {
        val p = assertNotNull(MediaPlaylist.parse(fixture("twitch/media-stitched-preroll.m3u8")))
        val ad = p.dateRanges.first { it.klass == "twitch-stitched-ad" }
        assertNotNull(ad.duration)
        assertEquals("PREROLL", ad.attributes["X-TV-TWITCH-AD-ROLL-TYPE"])
    }

    @Test
    fun discontinuityAndTitlesAreKept() {
        val text =
            "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:2.000,live\na.ts\n#EXT-X-DISCONTINUITY\n" +
                "#EXTINF:3.000,Amazon|0\nb.ts\n#EXTINF:2.000,live\nc.ts\n"
        val p = assertNotNull(MediaPlaylist.parse(text))
        assertEquals(listOf(false, true, false), p.segments.map { it.discontinuity })
        assertEquals("Amazon|0", p.segments[1].title)
        assertEquals(2.seconds, p.typicalSegment)
        assertFalse(p.hasPrefetch)
    }

    @Test
    fun brokenInputsAreNullOrKeepGoodSegments() {
        val files = fixtureDir("broken")
        assertEquals(3, files.size)
        for ((name, text) in files) {
            val p = MediaPlaylist.parse(text)
            if (p != null) {
                assertTrue(name == "truncated.m3u8" && p.segments.isNotEmpty(), name)
                assertTrue(p.segments.all { it.duration == 2.seconds })
            }
        }
        assertNull(MediaPlaylist.parse(fixture("broken/empty.m3u8")))
        assertNull(MediaPlaylist.parse(fixture("broken/html.m3u8")))
    }

    @Test
    fun badLinesAreSkipped() {
        val text = "#EXTM3U\n#EXTINF:abc,x\nbad.ts\n#EXT-X-PROGRAM-DATE-TIME:nonsense\n#EXTINF:2.0,live\nok.ts\n"
        val p = assertNotNull(MediaPlaylist.parse(text))
        assertEquals(1, p.segments.size)
        assertNull(p.segments[0].programDateTime)
    }

    @Test
    fun nonFiniteNumbersNeverThrow() {
        val text =
            "\uFEFF#EXTM3U\n#EXT-X-TARGETDURATION:NaN\n" +
                "#EXT-X-DATERANGE:ID=\"a\",CLASS=\"timestamp\",DURATION=NaN,PLANNED-DURATION=Infinity," +
                "X-SERVER-TIME=Infinity\n" +
                "#EXTINF:NaN,x\nbad.ts\n#EXTINF:-1,x\nneg.ts\n#EXTINF:2.0,live\nok.ts\n"
        val p = assertNotNull(MediaPlaylist.parse(text))
        assertEquals(1, p.segments.size)
        assertNull(p.targetDuration)
        assertNull(p.serverTime)
        assertNull(p.dateRanges.single().duration)
        assertNull(p.dateRanges.single().plannedDuration)
    }

    @Test
    fun decimalServerTimeIsExact() {
        val text =
            "#EXTM3U\n#EXT-X-DATERANGE:ID=\"a\",CLASS=\"timestamp\",X-SERVER-TIME=\"1794068458.92\"\n" +
                "#EXTINF:2.0,live\na.ts\n"
        assertEquals(
            kotlin.time.Instant.fromEpochMilliseconds(1794068458920),
            assertNotNull(MediaPlaylist.parse(text)).serverTime,
        )
    }

    @Test
    fun evenCountMedianTakesUpperMiddle() {
        val text = "#EXTM3U\n#EXTINF:2.0,a\na.ts\n#EXTINF:4.0,b\nb.ts\n"
        assertEquals(4.seconds, assertNotNull(MediaPlaylist.parse(text)).typicalSegment)
    }
}

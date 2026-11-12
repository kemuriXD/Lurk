package io.github.kemurixd.lurk.playback.hls

import io.github.kemurixd.lurk.playback.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MasterPlaylistTest {
    private val base = "https://video-weaver.example/v1/master.m3u8"

    @Test
    fun masterVariantsFromFixture() {
        val text = fixture("twitch/master.m3u8")
        val expected = text.lines().count { it.startsWith("#EXT-X-STREAM-INF") }
        val variants = MasterPlaylist.variants(text, base)
        assertEquals(expected, variants.size)
        assertEquals("1080p60", variants.first().name)
        assertEquals(1080, variants.first().height)
        assertEquals(1920, variants.first().width)
        assertEquals(60f, variants.first().frameRate)
        assertEquals(1, variants.count { it.audioOnly })
        assertTrue(variants.all { it.url.startsWith("https://") })
        assertEquals(variants.sortedByDescending { it.bandwidth }, variants)
        assertEquals("720p", variants.first { it.url.endsWith("720p30.m3u8") }.name)
    }

    @Test
    fun relativeUrisResolveAgainstBase() {
        val text =
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=640x360\nlow/index.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=200,RESOLUTION=1280x720\n/abs/hi.m3u8\n"
        val v = MasterPlaylist.variants(text, "https://h.example/a/b/master.m3u8")
        assertEquals("https://h.example/abs/hi.m3u8", v[0].url)
        assertEquals("https://h.example/a/b/low/index.m3u8", v[1].url)
    }

    @Test
    fun nameFromMediaGroupWins() {
        val text =
            listOf(
                "#EXTM3U",
                """#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",DEFAULT=YES""",
                """#EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=1920x1080,VIDEO="chunked",IVS-NAME="other"""",
                "https://h.example/v.m3u8",
                """#EXT-X-STREAM-INF:BANDWIDTH=50,RESOLUTION=640x360,STABLE-VARIANT-ID="stable"""",
                "https://h.example/w.m3u8",
                "#EXT-X-STREAM-INF:BANDWIDTH=40,RESOLUTION=320x180",
                "https://h.example/x.m3u8",
            ).joinToString("\n")
        val v = MasterPlaylist.variants(text, "https://h.example/m.m3u8")
        assertEquals(listOf("1080p60 (source)", "stable", "180p"), v.map { it.name })
    }

    @Test
    fun audioOnlyGroupWithoutResolution() {
        val text =
            listOf(
                "#EXTM3U",
                """#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only"""",
                """#EXT-X-STREAM-INF:BANDWIDTH=10,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="audio_only"""",
                "https://h.example/a.m3u8",
            ).joinToString("\n")
        assertTrue(MasterPlaylist.variants(text, "https://h.example/m.m3u8").single().audioOnly)
    }

    @Test
    fun garbageYieldsEmpty() {
        assertEquals(emptyList(), MasterPlaylist.variants("<html>nope</html>", "https://h.example/"))
        assertEquals(emptyList(), MasterPlaylist.variants("", "https://h.example/"))
    }

    @Test
    fun authorityOnlyBaseWithQueryDoesNotThrow() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8\n"
        val v = MasterPlaylist.variants(text, "https://h.example?x=a/b")
        assertEquals("https://h.example/low.m3u8", v.single().url)
    }

    @Test
    fun otherSchemesAreAbsoluteAndBomIsIgnored() {
        val text = "\uFEFF#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttp:foo/x.m3u8\n"
        assertEquals("http:foo/x.m3u8", MasterPlaylist.variants(text, "https://h.example/a/m.m3u8").single().url)
    }
}

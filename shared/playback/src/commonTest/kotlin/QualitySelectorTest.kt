package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.playback.hls.MasterPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualitySelectorTest {
    private val variants =
        MasterPlaylist.variants(fixture("twitch/master.m3u8"), "https://video-weaver.example/v1/master.m3u8")

    @Test
    fun maxHeightPrefersTheHigherFrameRate() {
        val v = QualitySelector.initial(variants, QualityPreference.MaxHeight(720))
        assertEquals(720, v?.height)
        assertEquals(60f, v?.frameRate)
    }

    @Test
    fun maxHeightBelowEveryVariantFallsBackToTheLowestVideo() {
        val v = QualitySelector.initial(variants, QualityPreference.MaxHeight(100))
        val lowest = variants.filterNot { it.audioOnly }.minBy { it.bandwidth }
        assertEquals(lowest, v)
        assertTrue(v?.audioOnly == false)
    }

    @Test
    fun audioOnlyPicksTheAudioVariant() {
        assertEquals(true, QualitySelector.initial(variants, QualityPreference.AudioOnly)?.audioOnly)
    }

    @Test
    fun audioOnlyWithoutAudioVariantFallsBackToTheLowestVideo() {
        val video = variants.filterNot { it.audioOnly }
        assertEquals(video.minBy { it.bandwidth }, QualitySelector.initial(video, QualityPreference.AudioOnly))
    }

    @Test
    fun autoIsNull() {
        assertNull(QualitySelector.initial(variants, QualityPreference.Auto))
    }

    @Test
    fun emptyListIsNullForEveryPreference() {
        listOf(QualityPreference.Auto, QualityPreference.MaxHeight(720), QualityPreference.AudioOnly).forEach {
            assertNull(QualitySelector.initial(emptyList(), it))
        }
    }
}

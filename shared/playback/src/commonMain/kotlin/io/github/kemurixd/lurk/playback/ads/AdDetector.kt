package io.github.kemurixd.lurk.playback.ads

import io.github.kemurixd.lurk.core.config.TwitchConfig
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.hls.DateRange
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import io.github.kemurixd.lurk.playback.hls.Segment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The DATERANGE classes that mark ads, read live from remote config. */
data class AdMarkers(
    val cueClasses: List<String>,
    val stitchedClasses: List<String>,
) {
    companion object {
        fun from(config: TwitchConfig): AdMarkers = AdMarkers(config.adCueClasses, config.stitchedAdClasses)
    }
}

/** A `twitch-maf-ad` style cue. The video stays live during a cue-only break. */
data class CueBreak(
    val id: String,
    val start: Instant,
    val planned: Duration,
)

/** A stretch of the stream that carries ad video. */
data class StitchedWindow(
    val start: Instant,
    val end: Instant,
    val roll: RollType,
    val podLength: Int,
)

data class PlaylistAdInfo(
    val cues: List<CueBreak>,
    val stitched: List<StitchedWindow>,
) {
    /** Only stitched windows count: cues never hide the video and never trigger the proxy. */
    val hasStitched: Boolean get() = stitched.isNotEmpty()

    companion object {
        val EMPTY = PlaylistAdInfo(emptyList(), emptyList())
    }
}

/** Finds ad markers in a Twitch media playlist. [inspect] never throws. */
class AdDetector(
    private val markers: () -> AdMarkers,
) {
    fun inspect(playlist: MediaPlaylist): PlaylistAdInfo =
        runCatching { detect(playlist, markers()) }.getOrDefault(PlaylistAdInfo.EMPTY)

    private fun detect(
        playlist: MediaPlaylist,
        m: AdMarkers,
    ): PlaylistAdInfo {
        val cues = playlist.dateRanges.mapNotNull { cue(it, m) }
        val byRange = merge(playlist.dateRanges.mapNotNull { stitchedRange(it, m) })
        val bySegment = titleRuns(playlist.segments, byRange)
        return PlaylistAdInfo(cues, (byRange + bySegment).sortedBy { it.start })
    }

    private fun cue(
        range: DateRange,
        m: AdMarkers,
    ): CueBreak? {
        val start = range.start
        return if (range.klass in m.cueClasses && start != null) {
            CueBreak(range.id, start, range.plannedDuration ?: DEFAULT_CUE)
        } else {
            null
        }
    }

    private fun isStitched(
        range: DateRange,
        m: AdMarkers,
    ): Boolean {
        val klass = range.klass
        return when {
            klass == QUARTILE_CLASS || klass in m.cueClasses -> false
            klass != null && (klass in m.stitchedClasses || klass.contains(STITCHED_HINT)) -> true
            range.id.startsWith(STITCHED_ID_PREFIX) -> true
            else -> range.attributes.keys.any { it.startsWith(AD_ATTRIBUTE_PREFIX) }
        }
    }

    private fun stitchedRange(
        range: DateRange,
        m: AdMarkers,
    ): StitchedWindow? {
        val start = range.start
        if (start == null || !isStitched(range, m)) return null
        val length = range.duration ?: range.plannedDuration ?: Duration.ZERO
        return StitchedWindow(
            start = start,
            end = start + length,
            roll = roll(range.attributes[ROLL_ATTRIBUTE]),
            podLength =
                range.attributes[POD_LENGTH_ATTRIBUTE]
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: 1,
        )
    }

    private fun roll(value: String?): RollType =
        when (value?.trim()?.uppercase()) {
            "PREROLL" -> RollType.PREROLL
            "MIDROLL" -> RollType.MIDROLL
            else -> RollType.UNKNOWN
        }

    /** Back-to-back ads of one pod (POD-POSITION 0, 1, ...) share a roll type and touch, so they become one window. */
    private fun merge(windows: List<StitchedWindow>): List<StitchedWindow> {
        val out = ArrayList<StitchedWindow>()
        for (w in windows.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && last.roll == w.roll && w.start - last.end <= MERGE_GAP) {
                out[out.lastIndex] =
                    last.copy(end = maxOf(last.end, w.end), podLength = maxOf(last.podLength, w.podLength))
            } else {
                out.add(w)
            }
        }
        return out
    }

    private fun titleRuns(
        segments: List<Segment>,
        covered: List<StitchedWindow>,
    ): List<StitchedWindow> {
        val out = ArrayList<StitchedWindow>()
        var run = ArrayList<Segment>()

        fun flush() {
            val first = run.firstOrNull()?.programDateTime
            if (first != null) {
                val total = run.fold(Duration.ZERO) { acc, s -> acc + s.duration }
                out.add(StitchedWindow(first, first + total, RollType.UNKNOWN, 1))
            }
            run = ArrayList()
        }
        for (s in segments) {
            if (isAdTitle(s.title) && !isCovered(s, covered)) run.add(s) else flush()
        }
        flush()
        return out
    }

    private fun isAdTitle(title: String): Boolean =
        title.isNotBlank() && !title.trim().equals("live", ignoreCase = true)

    private fun isCovered(
        segment: Segment,
        windows: List<StitchedWindow>,
    ): Boolean {
        val at = segment.programDateTime ?: return false
        return windows.any { at >= it.start && at < it.end }
    }

    private companion object {
        val DEFAULT_CUE = 60.seconds
        val MERGE_GAP = 1.seconds
        const val QUARTILE_CLASS = "twitch-ad-quartile"
        const val STITCHED_HINT = "stitched-ad"
        const val STITCHED_ID_PREFIX = "stitched-ad-"
        const val AD_ATTRIBUTE_PREFIX = "X-TV-TWITCH-AD-"
        const val ROLL_ATTRIBUTE = "X-TV-TWITCH-AD-ROLL-TYPE"
        const val POD_LENGTH_ATTRIBUTE = "X-TV-TWITCH-AD-POD-LENGTH"
    }
}

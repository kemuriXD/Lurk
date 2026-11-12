package io.github.kemurixd.lurk.playback.hls

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** An `#EXT-X-DATERANGE` entry. */
data class DateRange(
    val id: String,
    val klass: String?,
    val start: Instant?,
    val duration: Duration?,
    val plannedDuration: Duration?,
    val attributes: Map<String, String>,
)

/** One media segment. The URI is not kept: ad detection only needs timing, title and discontinuities. */
data class Segment(
    val duration: Duration,
    val title: String,
    val programDateTime: Instant?,
    val discontinuity: Boolean,
)

data class MediaPlaylist(
    val targetDuration: Duration?,
    val segments: List<Segment>,
    val dateRanges: List<DateRange>,
    val serverTime: Instant?,
    val hasPrefetch: Boolean,
) {
    /** Median `#EXTINF`. Twitch declares TARGETDURATION 6 while its segments are 2 s. */
    val typicalSegment: Duration? =
        segments
            .map { it.duration }
            .sorted()
            .let { if (it.isEmpty()) null else it[it.size / 2] }

    companion object {
        private const val MILLIS = 1000.0
        private const val MAX_EPOCH_SECONDS = 1e11

        /** Null when the text lacks `#EXTM3U` or has no usable `#EXTINF`. Bad lines are skipped. */
        fun parse(text: String): MediaPlaylist? {
            val lines =
                text
                    .removePrefix("\uFEFF")
                    .lineSequence()
                    .map { it.trim() }
                    .toList()
            val reader = Reader()
            if (lines.any { it.startsWith("#EXTM3U") }) lines.forEach(reader::accept)
            return reader.result()
        }

        private fun parseInstant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()

        private fun seconds(s: String): Duration? =
            s
                .trim()
                .toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0 }
                ?.seconds
    }

    private class Reader {
        private var target: Duration? = null
        private var prefetch = false
        private val segments = ArrayList<Segment>()
        private val ranges = ArrayList<DateRange>()
        private var pending: Pair<Duration, String>? = null
        private var pdt: Instant? = null
        private var discontinuity = false

        fun accept(line: String) {
            when {
                line.startsWith(TARGET) -> target = seconds(line.removePrefix(TARGET))
                line.startsWith(DATERANGE) -> dateRange(line.removePrefix(DATERANGE))?.let(ranges::add)
                line.startsWith(PDT) -> pdt = parseInstant(line.removePrefix(PDT).trim())
                line == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                line.startsWith("#EXT-X-TWITCH-PREFETCH") -> prefetch = true
                line.startsWith(EXTINF) -> pending = extinf(line.removePrefix(EXTINF))
                line.isNotEmpty() && !line.startsWith("#") -> segmentUri()
            }
        }

        private fun segmentUri() {
            pending?.let { (d, title) -> segments.add(Segment(d, title, pdt, discontinuity)) }
            pending = null
            pdt = null
            discontinuity = false
        }

        fun result(): MediaPlaylist? =
            if (segments.isEmpty()) {
                null
            } else {
                MediaPlaylist(target, segments, ranges, serverTime(), prefetch)
            }

        private fun serverTime(): Instant? =
            ranges
                .firstOrNull { it.klass == "timestamp" && it.attributes.containsKey("X-SERVER-TIME") }
                ?.attributes
                ?.get("X-SERVER-TIME")
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0 && it < MAX_EPOCH_SECONDS }
                ?.let { Instant.fromEpochMilliseconds((it * MILLIS).toLong()) }

        private fun extinf(rest: String): Pair<Duration, String>? {
            val duration = seconds(rest.substringBefore(',')) ?: return null
            return duration to rest.substringAfter(',', "").trim()
        }

        private fun dateRange(list: String): DateRange? {
            val a = parseAttributes(list)
            val id = a["ID"] ?: return null
            return DateRange(
                id = id,
                klass = a["CLASS"],
                start = a["START-DATE"]?.let(::parseInstant),
                duration = a["DURATION"]?.let(::seconds),
                plannedDuration = a["PLANNED-DURATION"]?.let(::seconds),
                attributes = a,
            )
        }
    }
}

private const val EXTINF = "#EXTINF:"
private const val DATERANGE = "#EXT-X-DATERANGE:"
private const val PDT = "#EXT-X-PROGRAM-DATE-TIME:"
private const val TARGET = "#EXT-X-TARGETDURATION:"

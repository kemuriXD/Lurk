package io.github.kemurixd.lurk.playback.ads

import io.github.kemurixd.lurk.playback.AdState
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * Follows Twitch ad breaks against the stream's own clock and publishes them as [state].
 *
 * The device's wall clock is never read. Stream time comes from the playlists (`X-SERVER-TIME`, or before that
 * the end of the last segment) plus the time elapsed on [timeSource] since then.
 *
 * [onMediaPlaylist], [reportPlayhead] and [close] may be called from any thread. Each one posts its input to a
 * serial view of [scope]'s dispatcher, and the ticker runs there too, so all tracker state is read and written
 * by one task at a time. A posted input reaches [state] once that task has run. [scope] needs a dispatcher that
 * really dispatches (not `Dispatchers.Unconfined`); a scope without one ticks on `Dispatchers.Default`.
 */
class AdTracker(
    private val detector: AdDetector,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
    private val viaProxy: () -> Boolean,
) {
    private val mutableState = MutableStateFlow<AdState>(AdState.None)

    /** De-duplicated; `remaining` is rounded down to whole seconds, so a countdown changes once a second. */
    val state: StateFlow<AdState> = mutableState.asStateFlow()

    private val serial =
        ((scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher) ?: Dispatchers.Default)
            .limitedParallelism(1)

    // Everything below is only touched by tasks on `serial`.
    private var clock: StreamClock? = null
    private var playhead: Playhead? = null
    private val seenCues = HashSet<String>()
    private val cues = ArrayList<Window>()
    private var stitched = listOf<StitchedWindow>()
    private var ticker: Job? = null
    private var closed = false

    fun onMediaPlaylist(playlist: MediaPlaylist) {
        val at = timeSource.markNow()
        val info = detector.inspect(playlist)
        val serverTime = playlist.serverTime
        val edge = liveEdge(playlist)
        post {
            moveClock(serverTime, edge, at)
            register(info)
        }
    }

    /** The program date-time the player is showing. It is the position for 5 s after the report. */
    fun reportPlayhead(programDateTime: Instant) {
        val at = timeSource.markNow()
        post {
            // Reports from different threads may be posted out of order; the newest one wins.
            if (playhead.let { it == null || at > it.at }) playhead = Playhead(programDateTime, at)
        }
    }

    /** Stops the ticker, forgets every window and publishes [AdState.None]. Later input is ignored. */
    fun close() {
        scope.launch(serial) {
            closed = true
            ticker?.cancel()
            ticker = null
            cues.clear()
            stitched = emptyList()
            mutableState.value = AdState.None
        }
    }

    private fun post(block: () -> Unit) {
        scope.launch(serial) {
            if (!closed) {
                block()
                schedule(recompute())
            }
        }
    }

    /**
     * `X-SERVER-TIME` is the time the playlist was created, so it repeats unchanged in every refresh. It moves the
     * clock only when it is ahead of it: the first time, or for a newer playlist. The live edge is only used until
     * a server time arrives.
     */
    private fun moveClock(
        serverTime: Instant?,
        edge: Instant?,
        at: ComparableTimeMark,
    ) {
        val current = clock
        when {
            serverTime != null -> {
                // The first server time replaces a clock taken from the live edge.
                if (current?.fromServer != true || serverTime > current.at(at)) {
                    clock = StreamClock(serverTime, at, fromServer = true)
                }
            }

            // Once a server time is known, the live edge is ignored.
            edge != null && current?.fromServer != true -> {
                if (current == null || edge > current.at(at)) clock = StreamClock(edge, at, fromServer = false)
            }
        }
    }

    private fun register(info: PlaylistAdInfo) {
        for (cue in info.cues) {
            if (seenCues.add(cue.id)) cues.add(Window(cue.start, cue.start + cue.planned))
        }
        if (info.stitched.isNotEmpty()) stitched = merge(stitched + info.stitched)
    }

    /** Publishes the state for now and returns how long until it can next change, or null when no window is open. */
    private fun recompute(): Duration? {
        val now = timeSource.markNow()
        val streamNow = clock?.at(now)
        if (streamNow != null) {
            val cutoff = streamNow - KEEP_ENDED
            cues.removeAll { it.end < cutoff }
            stitched = stitched.filterNot { it.end < cutoff }
        }
        val position = position(now, streamNow)
        mutableState.value = position?.let(::stateAt) ?: AdState.None
        return position?.let { nextChange(it, now) }
    }

    private fun position(
        now: ComparableTimeMark,
        streamNow: Instant?,
    ): Instant? {
        val p = playhead
        return if (p != null && now - p.at < PLAYHEAD_FRESH) p.programDateTime + (now - p.at) else streamNow
    }

    private fun stateAt(position: Instant): AdState {
        val ad = stitched.firstOrNull { position >= it.start && position < it.end }
        if (ad != null) return AdState.Playing(wholeSeconds(ad.end - position), ad.roll, ad.podLength)
        val cueEnd = cues.filter { position >= it.start && position < it.end }.maxOfOrNull { it.end }
        return if (cueEnd != null) AdState.Avoided(viaProxy(), wholeSeconds(cueEnd - position)) else AdState.None
    }

    private fun nextChange(
        position: Instant,
        now: ComparableTimeMark,
    ): Duration? {
        val open = (stitched.map { Window(it.start, it.end) } + cues).filter { position < it.end }
        if (open.isEmpty()) return null
        val waits =
            open.map { if (position < it.start) it.start - position else untilNextSecond(it.end - position) }
        val stale = playhead?.let { PLAYHEAD_FRESH - (now - it.at) }?.takeIf { it.isPositive() }
        return (waits + listOfNotNull(stale)).min().coerceAtMost(TICK)
    }

    private fun schedule(wait: Duration?) {
        ticker?.cancel()
        ticker =
            wait?.let { first ->
                scope.launch(serial) {
                    var next: Duration? = first
                    while (next != null) {
                        delay(next)
                        next = recompute()
                    }
                }
            }
    }

    private class StreamClock(
        val instant: Instant,
        val mark: ComparableTimeMark,
        val fromServer: Boolean,
    ) {
        fun at(time: ComparableTimeMark): Instant = instant + (time - mark)
    }

    private class Playhead(
        val programDateTime: Instant,
        val at: ComparableTimeMark,
    )

    private data class Window(
        val start: Instant,
        val end: Instant,
    )

    private companion object {
        val PLAYHEAD_FRESH = 5.seconds
        val KEEP_ENDED = 5.minutes
        val TICK = 1.seconds

        /** The end of the last segment, counting on from the last `PROGRAM-DATE-TIME`. */
        fun liveEdge(playlist: MediaPlaylist): Instant? {
            var edge: Instant? = null
            for (segment in playlist.segments) {
                edge = (segment.programDateTime ?: edge)?.plus(segment.duration)
            }
            return edge
        }

        /** Overlapping or touching windows become one; the first known roll type and the largest pod are kept. */
        fun merge(windows: List<StitchedWindow>): List<StitchedWindow> {
            val out = ArrayList<StitchedWindow>()
            for (w in windows.sortedBy { it.start }) {
                val last = out.lastOrNull()
                if (last != null && w.start <= last.end) {
                    out[out.lastIndex] =
                        last.copy(
                            end = maxOf(last.end, w.end),
                            roll = if (last.roll == RollType.UNKNOWN) w.roll else last.roll,
                            podLength = maxOf(last.podLength, w.podLength),
                        )
                } else {
                    out.add(w)
                }
            }
            return out
        }

        fun wholeSeconds(d: Duration): Duration = d.inWholeSeconds.seconds

        /** How long until [remaining], shown in whole seconds, shows a new value; at under 1 s, until it ends. */
        fun untilNextSecond(remaining: Duration): Duration {
            val whole = wholeSeconds(remaining)
            return if (whole == Duration.ZERO) remaining else remaining - whole + 1.milliseconds
        }
    }
}

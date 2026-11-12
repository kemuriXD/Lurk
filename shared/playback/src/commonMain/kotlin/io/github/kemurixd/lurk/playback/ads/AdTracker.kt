package io.github.kemurixd.lurk.playback.ads

import io.github.kemurixd.lurk.playback.AdState
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
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
 * The device's wall clock is never read. The stream clock is the later of two anchors, each plus the time elapsed
 * on [timeSource] since it was taken: `X-SERVER-TIME` (the playlist's creation time, repeated unchanged in every
 * refresh) and the live edge (the end of the last segment). Both are lower bounds on real stream time, so the
 * later one is the closer. Each anchor only moves forward, so the clock never runs backwards. A preroll that is
 * already behind the live edge when the playlist is fetched reads as over; a player that starts at the ads
 * reports its position through [reportPlayhead].
 *
 * [onMediaPlaylist], [reportPlayhead] and [close] may be called from any thread. [onMediaPlaylist] runs
 * [AdDetector.inspect] on the caller's thread, then each call posts its input to a serial view of [scope]'s
 * dispatcher, where the ticker also runs. All tracker state is therefore read and written by one task at a time,
 * and a posted input reaches [state] once that task has run. [scope] needs a dispatcher that really dispatches
 * (not an unconfined one); a scope without a dispatcher uses `Dispatchers.Default`.
 *
 * The tracker's coroutines run under its own child Job of [scope]. Call [close] when done with it.
 */
internal class AdTracker(
    private val detector: AdDetector,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
    private val viaProxy: () -> Boolean,
) {
    private val mutableState = MutableStateFlow<AdState>(AdState.None)

    /** De-duplicated; `remaining` is rounded down to whole seconds, so a countdown changes once a second. */
    val state: StateFlow<AdState> = mutableState.asStateFlow()

    private val serial = serialDispatcher(scope)
    private val job = Job(scope.coroutineContext[Job])

    @Volatile
    private var closed = false

    // Everything below is only touched by tasks on `serial`.
    private var serverClock: Anchor? = null
    private var edgeClock: Anchor? = null
    private var playhead: Anchor? = null
    private val seenCues = HashSet<String>()
    private val cues = ArrayList<Window>()
    private var stitched = listOf<StitchedWindow>()
    private var ticker: Job? = null
    private var reloadedAt = 0

    /**
     * Feeds one refresh and returns what [AdDetector] found in it, so the caller need not inspect it twice.
     * [generation] is the route generation the refresh was fetched on. A refresh from before the last [onReload]
     * is dropped, since its ads belong to a stream the player no longer loads.
     */
    fun onMediaPlaylist(
        playlist: MediaPlaylist,
        generation: Int = 0,
    ): PlaylistAdInfo {
        val at = timeSource.markNow()
        val info = detector.inspect(playlist)
        val serverTime = playlist.serverTime
        val edge = liveEdge(playlist)
        post {
            if (generation < reloadedAt) return@post
            serverClock = forward(serverClock, serverTime, at)
            edgeClock = forward(edgeClock, edge, at)
            for (cue in info.cues) {
                if (seenCues.add(cue.id)) cues.add(Window(cue.start, cue.start + cue.planned))
            }
            if (info.stitched.isNotEmpty()) stitched = merge(stitched + info.stitched)
        }
        return info
    }

    /** The program date-time the player is showing. It is the position for 5 s after the report. */
    fun reportPlayhead(programDateTime: Instant) {
        val at = timeSource.markNow()
        post {
            // Reports from different threads may be posted out of order; the newest one wins.
            if (playhead.let { it == null || at > it.mark }) playhead = Anchor(programDateTime, at)
        }
    }

    /**
     * The session moved to another route with a Reload, so the stitched ads seen on the old route are not in the
     * stream the player now loads; they are forgotten. Cue windows, and the cue IDs already seen, stay. [generation]
     * is the new route's: later input from an older generation is dropped. Both calls go through the same serial
     * queue, so a refresh that was in flight across the reload cannot bring the old ads back.
     */
    fun onReload(generation: Int) {
        post {
            reloadedAt = maxOf(reloadedAt, generation)
            stitched = emptyList()
        }
    }

    /** Publishes [AdState.None] and cancels the tracker's coroutines at once. Later input is ignored. */
    fun close() {
        closed = true
        mutableState.value = AdState.None
        job.cancel()
    }

    private fun post(block: () -> Unit) {
        if (closed) return
        scope.launch(job + serial) {
            if (!closed) {
                block()
                schedule(recompute())
            }
        }
    }

    /** Publishes the state for now and returns how long until it can next change, or null when no window is open. */
    private fun recompute(): Duration? {
        val now = timeSource.markNow()
        val streamNow = listOfNotNull(serverClock?.at(now), edgeClock?.at(now)).maxOrNull()
        if (streamNow != null) {
            val cutoff = streamNow - KEEP_ENDED
            cues.removeAll { it.end < cutoff }
            stitched = stitched.filterNot { it.end < cutoff }
        }
        val position = position(now, streamNow)
        publish(position?.let(::stateAt) ?: AdState.None)
        return position?.let { nextChange(it, streamNow, now) }
    }

    /** A task that started before [close] may finish after it; re-checking afterwards keeps the last word None. */
    private fun publish(value: AdState) {
        if (closed) return
        mutableState.value = value
        if (closed) mutableState.value = AdState.None
    }

    private fun position(
        now: ComparableTimeMark,
        streamNow: Instant?,
    ): Instant? {
        val p = playhead
        return if (p != null && now - p.mark < PLAYHEAD_FRESH) p.at(now) else streamNow
    }

    private fun stateAt(position: Instant): AdState {
        val ad = stitched.firstOrNull { position >= it.start && position < it.end }
        if (ad != null) return AdState.Playing(wholeSeconds(ad.end - position), ad.roll, ad.podLength)
        val cueEnd = cues.filter { position >= it.start && position < it.end }.maxOfOrNull { it.end }
        return if (cueEnd != null) AdState.Avoided(viaProxy(), wholeSeconds(cueEnd - position)) else AdState.None
    }

    private fun nextChange(
        position: Instant,
        streamNow: Instant?,
        now: ComparableTimeMark,
    ): Duration? {
        val windows = stitched.map { Window(it.start, it.end) } + cues
        val waits =
            windows
                .filter { position < it.end }
                .map { if (position < it.start) it.start - position else untilNextSecond(it.end - position) }
        // When a fresh playhead goes stale the position falls back to the stream clock, which may be inside a
        // window even when the playhead is not.
        val stale =
            playhead
                ?.let { PLAYHEAD_FRESH - (now - it.mark) }
                ?.takeIf { it.isPositive() && streamNow != null && windows.any { w -> streamNow < w.end } }
        val all = waits + listOfNotNull(stale)
        return if (all.isEmpty()) null else all.min().coerceAtMost(TICK)
    }

    private fun schedule(wait: Duration?) {
        ticker?.cancel()
        ticker =
            wait?.let { first ->
                scope.launch(job + serial) {
                    var next: Duration? = first
                    while (next != null) {
                        delay(next)
                        next = recompute()
                    }
                }
            }
    }

    /** An instant seen at [mark] on the time source; it moves on with elapsed time. */
    private class Anchor(
        val instant: Instant,
        val mark: ComparableTimeMark,
    ) {
        fun at(time: ComparableTimeMark): Instant = instant + (time - mark)
    }

    private data class Window(
        val start: Instant,
        val end: Instant,
    )

    private companion object {
        val PLAYHEAD_FRESH = 5.seconds
        val KEEP_ENDED = 5.minutes
        val TICK = 1.seconds

        fun serialDispatcher(scope: CoroutineScope): CoroutineDispatcher {
            val dispatcher =
                scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default
            require(dispatcher is MainCoroutineDispatcher || dispatcher.isDispatchNeeded(EmptyCoroutineContext)) {
                "AdTracker needs a scope whose dispatcher dispatches; an unconfined one cannot run its tasks in turn"
            }
            return dispatcher.limitedParallelism(1)
        }

        /** Moves [anchor] to [value] only if [value] is ahead of where the anchor has got to. */
        fun forward(
            anchor: Anchor?,
            value: Instant?,
            at: ComparableTimeMark,
        ): Anchor? =
            when {
                value == null -> anchor
                anchor == null || value > anchor.at(at) -> Anchor(value, at)
                else -> anchor
            }

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

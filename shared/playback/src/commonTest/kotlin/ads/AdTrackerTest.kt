package io.github.kemurixd.lurk.playback.ads

import app.cash.turbine.test
import io.github.kemurixd.lurk.playback.AdState
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.fixture
import io.github.kemurixd.lurk.playback.fixtureDir
import io.github.kemurixd.lurk.playback.hls.MediaPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AdTrackerTest {
    private val markers = AdMarkers(listOf("twitch-maf-ad"), listOf("twitch-stitched-ad"))

    /** A child of backgroundScope, so a test can count the jobs the tracker leaves behind. */
    private fun TestScope.trackerScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

    private fun TestScope.tracker(
        scope: CoroutineScope = trackerScope(),
        viaProxy: () -> Boolean = { false },
    ): AdTracker = AdTracker(AdDetector { markers }, scope, testScheduler.timeSource, viaProxy)

    private fun parse(text: String): MediaPlaylist = assertNotNull(MediaPlaylist.parse(text))

    private fun load(name: String): MediaPlaylist = parse(fixture("twitch/$name"))

    /** Active jobs directly in [this] scope. The tracker keeps one there, its own Job, until it is closed. */
    private fun CoroutineScope.activeChildren(): Int = coroutineContext.job.children.count { it.isActive }

    /** Active coroutines under the tracker's own Job: queued input and the ticker. */
    private fun CoroutineScope.activeTasks(): Int =
        coroutineContext.job.children.sumOf { owner -> owner.children.count { it.isActive } }

    /** Feeds the refreshes with the spacing of their original fetch times (`NNN-<ms>.m3u8`). */
    private suspend fun replay(
        tracker: AdTracker,
        files: List<Pair<String, String>>,
    ) {
        var previous: Long? = null
        for ((name, text) in files) {
            val fetchedAt = name.substringAfter('-').substringBefore('.').toLong()
            previous?.let { delay((fetchedAt - it).milliseconds) }
            previous = fetchedAt
            tracker.onMediaPlaylist(parse(text))
        }
    }

    /** Records every state with the virtual time it was published at. */
    private fun TestScope.record(tracker: AdTracker): List<Pair<Long, AdState>> {
        val log = mutableListOf<Pair<Long, AdState>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            tracker.state.collect { log += currentTime to it }
        }
        return log
    }

    /**
     * The preroll playlist as an earlier refresh would have shown it: cut after its third ad segment, so the live
     * edge (22:33:25.120 + 2.002 s = 22:33:27.122) is inside the ad. The full fixture's live edge is past the ad.
     */
    private fun prerollMidAd(): String {
        val out = ArrayList<String>()
        var segments = 0
        for (line in fixture("twitch/media-stitched-preroll.m3u8").lines()) {
            out += line
            if (line.isNotBlank() && !line.startsWith("#") && ++segments == 3) break
        }
        val text = out.joinToString("\n")
        assertEquals(Instant.parse("2026-11-07T22:33:25.120Z"), parse(text).segments.last().programDateTime)
        return text
    }

    private fun liveEdge(playlist: MediaPlaylist): Instant {
        val last = playlist.segments.last()
        return assertNotNull(last.programDateTime) + last.duration
    }

    @Test
    fun cueBreakReplay() =
        runTest {
            val tracker = tracker()
            backgroundScope.launch { replay(tracker, fixtureDir("twitch/cue-break")) }
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                // The cue first shows up in refresh 003, 2.009 s after its START-DATE on the stream clock.
                assertEquals(AdState.Avoided(viaProxy = false, remaining = 57.seconds), awaitItem())
                assertEquals(CUE_BREAK_SEEN, currentTime)
                for (s in 56 downTo 0) {
                    assertEquals(AdState.Avoided(viaProxy = false, remaining = s.seconds), awaitItem())
                    assertEquals(CUE_BREAK_END - 999 - s * 1000L, currentTime)
                }
                assertEquals(AdState.None, awaitItem())
                assertEquals(CUE_BREAK_END, currentTime)
                advanceTimeBy(10.minutes)
                expectNoEvents()
            }
        }

    @Test
    fun lingeringCueIsOneBreak() =
        runTest {
            val tracker = tracker()
            val cue = fixture("twitch/media-cue.m3u8")
            backgroundScope.launch {
                repeat(50) {
                    tracker.onMediaPlaylist(parse(cue))
                    delay(2.seconds)
                }
            }
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                assertEquals(AdState.Avoided(viaProxy = false, remaining = 58.seconds), awaitItem())
                assertEquals(0, currentTime)
                for (s in 57 downTo 0) assertEquals(AdState.Avoided(false, s.seconds), awaitItem())
                assertEquals(AdState.None, awaitItem())
                assertEquals(MEDIA_CUE_END, currentTime)

                // The same cue ID with a START-DATE moved ahead of the stream clock: no second break.
                advanceTimeBy(60_000 - currentTime)
                val moved =
                    cue.replace(
                        "START-DATE=\"2026-11-07T16:24:06.531Z\"",
                        "START-DATE=\"2026-11-07T16:25:15.000Z\"",
                    )
                assertNotEquals(cue, moved)
                tracker.onMediaPlaylist(parse(moved))
                advanceTimeBy(10.minutes)
                expectNoEvents()
            }
        }

    @Test
    fun stitchedPlaysWithCountdown() =
        runTest {
            val tracker = tracker()
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                tracker.onMediaPlaylist(parse(prerollMidAd()))
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(0, currentTime)
                for (s in 8 downTo 0) {
                    assertEquals(AdState.Playing(s.seconds, RollType.PREROLL, 1), awaitItem())
                    assertEquals(PREROLL_END - 999 - s * 1000L, currentTime)
                }
                assertEquals(AdState.None, awaitItem())
                assertEquals(PREROLL_END, currentTime)
            }
        }

    @Test
    fun prerollBehindLiveEdgeNeedsPlayhead() =
        runTest {
            // At fetch, the full fixture's live edge (22:33:48.333) is 12 s past the ad, so on the
            // stream clock the ad is over.
            val playlist = load("media-stitched-preroll.m3u8")
            val adStart =
                AdDetector { markers }
                    .inspect(playlist)
                    .stitched
                    .single()
                    .start
            val tracker = tracker()
            tracker.onMediaPlaylist(playlist)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)

            // A player that starts at the ads reports where it is.
            tracker.reportPlayhead(adStart)
            runCurrent()
            assertEquals(AdState.Playing(15.seconds, RollType.PREROLL, 1), tracker.state.value)
        }

    @Test
    fun inputFromBeforeARouteReloadIsDropped() =
        runTest {
            val tracker = tracker()
            tracker.onMediaPlaylist(parse(prerollMidAd()), generation = 0)
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)
            tracker.onReload(generation = 1)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            // A refresh fetched on the old route lands after the reload: it is ignored.
            tracker.onMediaPlaylist(parse(prerollMidAd()), generation = 0)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            // One fetched on the new route still counts.
            tracker.onMediaPlaylist(parse(prerollMidAd()), generation = 1)
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)
            tracker.close()
        }

    @Test
    fun stitchedWinsOverCue() =
        runTest {
            val cue =
                "#EXT-X-DATERANGE:ID=\"maf-ad-1\",CLASS=\"twitch-maf-ad\"," +
                    "START-DATE=\"2026-11-07T22:33:21.117Z\",PLANNED-DURATION=60.000"
            val text = prerollMidAd().replaceFirst("#EXT-X-DISCONTINUITY", "$cue\n#EXT-X-DISCONTINUITY")
            val tracker = tracker()
            tracker.onMediaPlaylist(parse(text))
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)

            // Once the stitched ad is over, the cue that covers the same span is what is left.
            advanceTimeBy(PREROLL_END)
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 44.seconds), tracker.state.value)
        }

    @Test
    fun countdownIgnoresDeviceClock() =
        runTest {
            // The tracker takes no Clock at all, so no device clock value can reach it. Shifting every
            // timestamp by 3 h must leave the published states, and when they are published, unchanged.
            val files = fixtureDir("twitch/cue-break")
            val shiftedFiles = files.map { (name, text) -> name to shift(text, 3.hours) }
            assertEquals(
                assertNotNull(parse(files.first().second).serverTime) + 3.hours,
                parse(shiftedFiles.first().second).serverTime,
            )

            val plain = tracker()
            val shifted = tracker()
            val plainLog = record(plain)
            val shiftedLog = record(shifted)
            backgroundScope.launch { replay(plain, files) }
            backgroundScope.launch { replay(shifted, shiftedFiles) }
            advanceTimeBy(5.minutes)

            // None, Avoided 57 s down to 0 s, None.
            assertEquals(60, plainLog.size)
            assertEquals(plainLog, shiftedLog)
        }

    @Test
    fun playheadDelaysTheBreak() =
        runTest {
            // The cue-break replay with its cue turned into a 60 s stitched ad, so it keeps the real fetch timing.
            val files =
                fixtureDir("twitch/cue-break").map { (name, text) ->
                    name to text.replace("CLASS=\"twitch-maf-ad\"", "CLASS=\"twitch-stitched-ad\"")
                }
            assertTrue(files.any { "twitch-stitched-ad" in it.second })
            val firstEdge = liveEdge(parse(files.first().second))
            val live = tracker()
            val behind = tracker()
            val liveLog = record(live)
            val behindLog = record(behind)
            backgroundScope.launch { replay(live, files) }
            backgroundScope.launch { replay(behind, files) }
            // The player reports its program date-time every second, 8 s behind the stream clock.
            backgroundScope.launch {
                repeat(120) {
                    behind.reportPlayhead(firstEdge + currentTime.milliseconds - 8.seconds)
                    delay(1.seconds)
                }
            }
            advanceTimeBy(5.minutes)

            assertEquals(CUE_BREAK_SEEN to AdState.Playing(57.seconds, RollType.UNKNOWN, 1), liveLog[1])
            assertEquals(CUE_BREAK_END to AdState.None, liveLog.last())
            // Behind by 8 s, the player reaches START-DATE 8 s after the stream clock does, and sees all 60 s.
            assertEquals(DELAYED_START to AdState.Playing(60.seconds, RollType.UNKNOWN, 1), behindLog[1])
            assertEquals(DELAYED_START + 60_000 to AdState.None, behindLog.last())
            assertEquals(CUE_BREAK_END + 8_000, behindLog.last().first)
        }

    @Test
    fun stalePlayheadIsIgnored() =
        runTest {
            val playlist = parse(prerollMidAd())
            val adStart =
                AdDetector { markers }
                    .inspect(playlist)
                    .stitched
                    .single()
                    .start
            val tracker = tracker()
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                tracker.onMediaPlaylist(playlist)
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                // A player still 15 s before the ad.
                tracker.reportPlayhead(adStart - 15.seconds)
                assertEquals(AdState.None, awaitItem())
                assertEquals(0, currentTime)
                // With no newer report, after 5 s the stream clock is the position again.
                assertEquals(AdState.Playing(4.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(5_000, currentTime)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun playheadPastTheAdHandsBackToStreamClock() =
        runTest {
            val tracker = tracker()
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                tracker.onMediaPlaylist(parse(prerollMidAd()))
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                // Past the end of every window, so nothing is open at the position, but the ad is at the stream clock.
                tracker.reportPlayhead(Instant.parse("2026-11-07T22:33:40Z"))
                assertEquals(AdState.None, awaitItem())
                assertEquals(AdState.Playing(4.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(5_000, currentTime)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun viaProxyFlagFollowsRoute() =
        runTest {
            var proxied = true
            val tracker = tracker(viaProxy = { proxied })
            tracker.onMediaPlaylist(load("media-cue.m3u8"))
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = true, remaining = 58.seconds), tracker.state.value)

            proxied = false
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 57.seconds), tracker.state.value)
        }

    @Test
    fun liveEdgeLeadsStaleServerTime() =
        runTest {
            // media-cue's X-SERVER-TIME is 3 min behind its live edge (16:24:08.531): the live edge wins,
            // with or without a server time. That is 2 s past the cue's START-DATE.
            val text = fixture("twitch/media-cue.m3u8")
            val withServerTime = tracker()
            val without = tracker()
            withServerTime.onMediaPlaylist(parse(text))
            without.onMediaPlaylist(parse(withoutServerTime(text)))
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 58.seconds), withServerTime.state.value)
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 58.seconds), without.state.value)
        }

    @Test
    fun olderServerTimeNeverMovesClockBack() =
        runTest {
            val text = fixture("twitch/media-cue.m3u8")
            // A server time 30 s into the cue, ahead of the live edge, so it leads the clock.
            val ahead = text.replace("X-SERVER-TIME=\"1794068458.92\"", "X-SERVER-TIME=\"1794068676.531\"")
            assertEquals(Instant.parse("2026-11-07T16:24:36.531Z"), parse(ahead).serverTime)
            val tracker = tracker()
            tracker.onMediaPlaylist(parse(ahead))
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 30.seconds), tracker.state.value)

            // A later refresh carries the older server time; the clock keeps going from where it was.
            advanceTimeBy(1.seconds)
            tracker.onMediaPlaylist(parse(text))
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 29.seconds), tracker.state.value)
        }

    @Test
    fun stitchedWindowIsExtended() =
        runTest {
            val text = prerollMidAd()
            val longer = text.replace("DURATION=15.217", "DURATION=25.217")
            assertNotEquals(text, longer)
            val tracker = tracker()
            tracker.onMediaPlaylist(parse(text))
            advanceTimeBy(1.seconds)
            tracker.onMediaPlaylist(parse(longer))
            runCurrent()
            assertEquals(AdState.Playing(18.seconds, RollType.PREROLL, 1), tracker.state.value)

            // A refresh that shows less of the ad never shortens the window.
            advanceTimeBy(1.seconds)
            tracker.onMediaPlaylist(parse(text))
            runCurrent()
            assertEquals(AdState.Playing(17.seconds, RollType.PREROLL, 1), tracker.state.value)
        }

    @Test
    fun oldWindowsAreForgotten() =
        runTest {
            val playlist = parse(prerollMidAd())
            val adStart =
                AdDetector { markers }
                    .inspect(playlist)
                    .stitched
                    .single()
                    .start
            val tracker = tracker()
            tracker.onMediaPlaylist(playlist)
            advanceTimeBy(4.minutes)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)

            // 4 min after the ad, a player that is back inside it still sees it.
            tracker.reportPlayhead(adStart + 1.seconds)
            runCurrent()
            assertEquals(AdState.Playing(14.seconds, RollType.PREROLL, 1), tracker.state.value)

            // Ended more than 5 min ago: forgotten.
            advanceTimeBy(2.minutes)
            tracker.reportPlayhead(adStart + 1.seconds)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
        }

    @Test
    fun noTickingWhenIdle() =
        runTest {
            val scope = trackerScope()
            val tracker = tracker(scope)
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                tracker.onMediaPlaylist(parse(prerollMidAd()))
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(1, scope.activeTasks())

                advanceTimeBy(PREROLL_END)
                runCurrent()
                assertEquals(AdState.None, expectMostRecentItem())
                assertEquals(0, scope.activeTasks())

                advanceTimeBy(10.minutes)
                expectNoEvents()
                assertEquals(0, scope.activeTasks())
            }
        }

    @Test
    fun closeStopsTracking() =
        runTest {
            val scope = trackerScope()
            val tracker = tracker(scope)
            tracker.onMediaPlaylist(parse(prerollMidAd()))
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)

            tracker.close()
            assertEquals(AdState.None, tracker.state.value)
            runCurrent()
            assertEquals(0, scope.activeChildren())

            tracker.onMediaPlaylist(parse(prerollMidAd()))
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            assertEquals(0, scope.activeChildren())
        }

    @Test
    fun closeThenCancelLeavesNone() =
        runTest {
            val scope = trackerScope()
            val tracker = tracker(scope)
            tracker.onMediaPlaylist(parse(prerollMidAd()))
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)

            // Input still queued when the session closes the tracker and cancels its scope at once.
            tracker.onMediaPlaylist(parse(prerollMidAd()))
            tracker.close()
            scope.cancel()
            assertEquals(AdState.None, tracker.state.value)
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            assertEquals(0, scope.activeChildren())
        }

    @Test
    fun unconfinedScopeIsRejected() =
        runTest {
            val unconfined = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            assertFailsWith<IllegalArgumentException> {
                AdTracker(AdDetector { markers }, unconfined, testScheduler.timeSource) { false }
            }
        }

    /** Moves every START-DATE, PROGRAM-DATE-TIME and X-SERVER-TIME by [by]. */
    private fun shift(
        text: String,
        by: Duration,
    ): String =
        text
            .replace(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z""")) {
                (Instant.parse(it.value) + by).toString()
            }.replace(Regex("""X-SERVER-TIME="([\d.]+)"""")) {
                val ms = (it.groupValues[1].toDouble() * 1000).roundToLong() + by.inWholeMilliseconds
                "X-SERVER-TIME=\"${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}\""
            }

    private fun withoutServerTime(text: String): String {
        val stripped = text.lines().filterNot { "X-SERVER-TIME" in it }.joinToString("\n")
        assertNotEquals(text, stripped)
        assertTrue(parse(stripped).serverTime == null)
        return stripped
    }

    private companion object {
        // Virtual time starts at the first playlist. The stream clock is the later of X-SERVER-TIME and the
        // live edge (end of the last segment), each plus the time since it was seen. In every fixture here
        // the live edge is the later one.

        // cue-break: refresh 003, the first with the cue, is fetched 1794071466931 - 1794071454922 ms in.
        const val CUE_BREAK_SEEN = 12_009L

        // cue-break: START-DATE 17:11:04.531 + 60 s, minus refresh 001's live edge 17:10:54.531.
        const val CUE_BREAK_END = 70_000L

        // The same break seen 8 s behind: START-DATE + 8 s minus the live edge 17:10:54.531.
        const val DELAYED_START = 18_000L

        // media-cue: START-DATE 16:24:06.531 + 60 s, minus the live edge 16:24:08.531.
        const val MEDIA_CUE_END = 58_000L

        // prerollMidAd: the ad ends 22:33:36.334 (21.117 + 15.217 s); live edge 22:33:27.122.
        const val PREROLL_END = 9_212L
    }
}

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

    private fun CoroutineScope.activeJobs(): Int = coroutineContext.job.children.count { it.isActive }

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

    @Test
    fun cueBreakReplay() =
        runTest {
            val tracker = tracker()
            backgroundScope.launch { replay(tracker, fixtureDir("twitch/cue-break")) }
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                assertEquals(AdState.Avoided(viaProxy = false, remaining = 60.seconds), awaitItem())
                assertEquals(CUE_BREAK_START, currentTime)
                for (s in 59 downTo 0) {
                    assertEquals(AdState.Avoided(viaProxy = false, remaining = s.seconds), awaitItem())
                    assertEquals(CUE_BREAK_START + (60 - s) * 1000 - 999, currentTime)
                }
                assertEquals(AdState.None, awaitItem())
                assertEquals(CUE_BREAK_START + 60_000, currentTime)
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
                repeat(150) {
                    tracker.onMediaPlaylist(parse(cue))
                    delay(2.seconds)
                }
            }
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                assertEquals(AdState.Avoided(viaProxy = false, remaining = 60.seconds), awaitItem())
                assertEquals(MEDIA_CUE_START, currentTime)
                for (s in 59 downTo 0) assertEquals(AdState.Avoided(false, s.seconds), awaitItem())
                assertEquals(AdState.None, awaitItem())
                assertEquals(MEDIA_CUE_START + 60_000, currentTime)

                // The same cue ID with a START-DATE moved ahead of the stream clock: no second break.
                advanceTimeBy(255_000 - currentTime)
                val moved =
                    cue.replace(
                        "START-DATE=\"2026-11-07T16:24:06.531Z\"",
                        "START-DATE=\"2026-11-07T16:25:20.000Z\"",
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
                tracker.onMediaPlaylist(load("media-stitched-preroll.m3u8"))
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(0, currentTime)
                for (s in 8 downTo 0) {
                    assertEquals(AdState.Playing(s.seconds, RollType.PREROLL, 1), awaitItem())
                    assertEquals(795L + (8 - s) * 1000, currentTime)
                }
                assertEquals(AdState.None, awaitItem())
                assertEquals(PREROLL_END, currentTime)
            }
        }

    @Test
    fun stitchedWinsOverCue() =
        runTest {
            val cue =
                "#EXT-X-DATERANGE:ID=\"maf-ad-1\",CLASS=\"twitch-maf-ad\"," +
                    "START-DATE=\"2026-11-07T22:33:21.117Z\",PLANNED-DURATION=60.000"
            val text =
                fixture("twitch/media-stitched-preroll.m3u8")
                    .replaceFirst("#EXT-X-DISCONTINUITY", "$cue\n#EXT-X-DISCONTINUITY")
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

            // None, Avoided 60 s down to 0 s, None.
            assertEquals(63, plainLog.size)
            assertEquals(plainLog, shiftedLog)
        }

    @Test
    fun playheadDelaysTheBreak() =
        runTest {
            val playlist = load("media-stitched-midroll-pod.m3u8")
            val serverTime = assertNotNull(playlist.serverTime)
            val live = tracker()
            val behind = tracker()
            val liveLog = record(live)
            val behindLog = record(behind)
            live.onMediaPlaylist(playlist)
            behind.onMediaPlaylist(playlist)
            // The player reports its program date-time every second, 8 s behind the stream clock.
            backgroundScope.launch {
                repeat(500) {
                    behind.reportPlayhead(serverTime + currentTime.milliseconds - 8.seconds)
                    delay(1.seconds)
                }
            }
            advanceTimeBy(500.seconds)

            val pod = AdState.Playing(30.seconds, RollType.MIDROLL, 2)
            assertEquals(MIDROLL_START, liveLog.first { it.second == pod }.first)
            assertEquals(MIDROLL_START + 8_000, behindLog.first { it.second == pod }.first)
            assertEquals(MIDROLL_END, liveLog.last().first)
            assertEquals(MIDROLL_END + 8_000, behindLog.last().first)
            assertEquals(AdState.None, behindLog.last().second)
            assertEquals(liveLog.map { it.second }, behindLog.map { it.second })
        }

    @Test
    fun stalePlayheadIsIgnored() =
        runTest {
            val playlist = load("media-stitched-preroll.m3u8")
            val serverTime = assertNotNull(playlist.serverTime)
            val tracker = tracker()
            tracker.state.test {
                assertEquals(AdState.None, awaitItem())
                tracker.onMediaPlaylist(playlist)
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                // A player still 20 s back, before the ad.
                tracker.reportPlayhead(serverTime - 20.seconds)
                assertEquals(AdState.None, awaitItem())
                assertEquals(0, currentTime)
                // With no newer report, after 5 s the stream clock is the position again.
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
            advanceTimeBy(MEDIA_CUE_START)
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = true, remaining = 60.seconds), tracker.state.value)

            proxied = false
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 59.seconds), tracker.state.value)
        }

    @Test
    fun liveEdgeIsTheClockBeforeServerTime() =
        runTest {
            // Without X-SERVER-TIME, the clock is the end of the last segment: 2 s past the cue's START-DATE.
            val tracker = tracker()
            tracker.onMediaPlaylist(parse(withoutServerTime(fixture("twitch/media-cue.m3u8"))))
            runCurrent()
            assertEquals(AdState.Avoided(viaProxy = false, remaining = 58.seconds), tracker.state.value)
        }

    @Test
    fun serverTimeTakesOverFromLiveEdge() =
        runTest {
            val text = fixture("twitch/media-stitched-preroll.m3u8")
            val tracker = tracker()
            // The live edge is 12 s past the end of the preroll.
            tracker.onMediaPlaylist(parse(withoutServerTime(text)))
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)

            advanceTimeBy(1.seconds)
            tracker.onMediaPlaylist(parse(text))
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)
        }

    @Test
    fun stitchedWindowIsExtended() =
        runTest {
            val text = fixture("twitch/media-stitched-preroll.m3u8")
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
            val playlist = load("media-stitched-preroll.m3u8")
            val adStart =
                assertNotNull(
                    AdDetector { markers }
                        .inspect(playlist)
                        .stitched
                        .single()
                        .start,
                )
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
                tracker.onMediaPlaylist(load("media-stitched-preroll.m3u8"))
                assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), awaitItem())
                assertEquals(1, scope.activeJobs())

                advanceTimeBy(PREROLL_END)
                runCurrent()
                assertEquals(AdState.None, expectMostRecentItem())
                assertEquals(0, scope.activeJobs())

                advanceTimeBy(10.minutes)
                expectNoEvents()
                assertEquals(0, scope.activeJobs())
            }
        }

    @Test
    fun closeStopsTracking() =
        runTest {
            val scope = trackerScope()
            val tracker = tracker(scope)
            tracker.onMediaPlaylist(load("media-stitched-preroll.m3u8"))
            runCurrent()
            assertEquals(AdState.Playing(9.seconds, RollType.PREROLL, 1), tracker.state.value)

            tracker.close()
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            assertEquals(0, scope.activeJobs())

            tracker.onMediaPlaylist(load("media-stitched-preroll.m3u8"))
            runCurrent()
            assertEquals(AdState.None, tracker.state.value)
            assertEquals(0, scope.activeJobs())
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
        // The clock is anchored at the first playlist's X-SERVER-TIME, at virtual time 0.
        // cue-break: cue START-DATE 17:11:04.531 minus X-SERVER-TIME 17:08:50.350.
        const val CUE_BREAK_START = 134_181L

        // media-cue: cue START-DATE 16:24:06.531 minus X-SERVER-TIME 16:20:58.920.
        const val MEDIA_CUE_START = 187_611L

        // media-stitched-preroll: ad ends 22:33:36.334; X-SERVER-TIME 22:33:26.540.
        const val PREROLL_END = 9_794L

        // media-stitched-midroll-pod: the pod runs 22:40:04.333 to 22:40:34.767; same X-SERVER-TIME.
        const val MIDROLL_START = 397_793L
        const val MIDROLL_END = 428_227L
    }
}

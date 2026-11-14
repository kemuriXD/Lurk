package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LastLiveLoaderTest {
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val then = Instant.parse("2026-01-01T00:00:00Z")

    private fun ref(login: String) = ChannelRef(Platform.KICK, login)

    /** A screen's scope on the test scheduler. Unlike `backgroundScope`, `advanceUntilIdle` runs its work. */
    private fun TestScope.screen(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    /** A loader on the test scheduler whose clock is virtual time. */
    private fun TestScope.loader(): LastLiveLoader {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
            }
        return LastLiveLoader(
            platforms = listOf(twitch, kick),
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            clock = clock,
        )
    }

    @Test
    fun onlyRequestedRowsAreFetched() =
        runTest {
            kick.lastLive = { Outcome.Success(then) }
            val loader = loader()

            loader.request(ref("streamer1"), screen())
            loader.request(ref("streamer2"), screen())
            advanceUntilIdle()

            assertEquals(listOf(ref("streamer1"), ref("streamer2")), kick.lastLiveRequests)
            assertEquals(mapOf(ref("streamer1") to then, ref("streamer2") to then), loader.times.first())
        }

    @Test
    fun theCacheStopsARefetchUntilItsTimeIsUp() =
        runTest {
            kick.lastLive = { Outcome.Success(then) }
            val loader = loader()

            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            loader.request(ref("streamer1"), screen())
            advanceTimeBy(9.minutes)
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            assertEquals(1, kick.lastLiveRequests.size)

            advanceTimeBy(2.minutes)
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            assertEquals(2, kick.lastLiveRequests.size)
        }

    @Test
    fun aRowAskedTwiceWhileLoadingIsFetchedOnce() =
        runTest {
            kick.lastLive = {
                delay(1_000)
                Outcome.Success(then)
            }
            val loader = loader()

            repeat(5) { loader.request(ref("streamer1"), screen()) }
            advanceUntilIdle()

            assertEquals(1, kick.lastLiveRequests.size)
        }

    @Test
    fun atMostThreeLookupsRunAtOnce() =
        runTest {
            var running = 0
            var peak = 0
            kick.lastLive = {
                running++
                peak = maxOf(peak, running)
                delay(1_000)
                running--
                Outcome.Success(then)
            }
            val loader = loader()

            repeat(10) { loader.request(ref("streamer$it"), screen()) }
            runCurrent()
            assertEquals(3, running)
            advanceUntilIdle()

            assertEquals(3, peak)
            assertEquals(10, loader.times.first().size)
        }

    /** Fills every permit with a slow lookup, so the next request waits its turn. */
    private fun TestScope.busy(loader: LastLiveLoader) {
        kick.lastLive = {
            delay(1_000)
            Outcome.Success(then)
        }
        repeat(3) { loader.request(ref("busy$it"), screen()) }
        runCurrent()
    }

    @Test
    fun aRowHiddenBeforeItsTurnIsNeverFetched() =
        runTest {
            val loader = loader()
            busy(loader)

            loader.request(ref("streamer1"), screen())
            runCurrent()
            loader.release(ref("streamer1"))
            advanceUntilIdle()

            assertTrue(ref("streamer1") !in kick.lastLiveRequests, "fetched: ${kick.lastLiveRequests}")
            assertTrue(ref("streamer1") !in loader.times.first())

            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            assertEquals(then, loader.times.first()[ref("streamer1")])
        }

    @Test
    fun aRowHiddenAndShownAgainBeforeItsTurnIsFetchedOnce() =
        runTest {
            val loader = loader()
            busy(loader)

            loader.request(ref("streamer1"), screen())
            loader.release(ref("streamer1"))
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()

            assertEquals(1, kick.lastLiveRequests.count { it == ref("streamer1") })
        }

    @Test
    fun aFailureIsRetriedAfterAMinute() =
        runTest {
            kick.lastLive = { Outcome.Failure(LurkError.Timeout) }
            val loader = loader()

            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            advanceTimeBy(59.seconds)
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            assertEquals(1, kick.lastLiveRequests.size)

            advanceTimeBy(2.seconds)
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()
            assertEquals(2, kick.lastLiveRequests.size)
        }

    @Test
    fun aFailureLeavesNoTime() =
        runTest {
            kick.lastLive =
                { if (it.login == "streamer1") Outcome.Failure(LurkError.Unexpected) else Outcome.Success(then) }
            val loader = loader()

            loader.request(ref("streamer1"), screen())
            loader.request(ref("streamer2"), screen())
            advanceUntilIdle()

            assertEquals(mapOf(ref("streamer2") to then), loader.times.first())
        }

    @Test
    fun aThrowingPlatformLeavesNoTime() =
        runTest {
            kick.lastLive = { error("boom") }
            val loader = loader()

            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()

            assertEquals(emptyMap(), loader.times.first())
        }

    @Test
    fun platformsThatCarryTheTimeAreNeverAsked() =
        runTest {
            val loader = loader()

            loader.request(ChannelRef(Platform.TWITCH, "streamer1"), screen())
            advanceUntilIdle()

            assertTrue(twitch.lastLiveRequests.isEmpty())
        }

    @Test
    fun aLookupCancelledWithItsScreenCanRunAgain() =
        runTest {
            kick.lastLive = {
                delay(1_000)
                Outcome.Success(then)
            }
            val loader = loader()
            val screen = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

            loader.request(ref("streamer1"), screen)
            runCurrent()
            screen.cancel()
            advanceUntilIdle()
            loader.request(ref("streamer1"), screen())
            advanceUntilIdle()

            assertEquals(2, kick.lastLiveRequests.size)
            assertEquals(mapOf(ref("streamer1") to then), loader.times.first())
        }

    @Test
    fun lookedUpTimesFillOnlyOfflineRowsWithoutOne() {
        val known = Instant.parse("2025-06-01T00:00:00Z")
        val bare = testChannel(Platform.KICK, login = "streamer1")
        val withTime = testChannel(Platform.KICK, login = "streamer2").copy(lastStream = LastStream(null, null, known))
        val times = mapOf(bare.ref to then, withTime.ref to then)

        val filled = listOf(bare, withTime).withLastLive(times)

        assertEquals(then, filled[0].lastStream?.startedAt)
        assertEquals(known, filled[1].lastStream?.startedAt)
    }
}

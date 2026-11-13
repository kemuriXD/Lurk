package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoadBothTest {
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val platforms = listOf(twitch, kick)

    private fun page(platform: Platform) = Page(listOf(testStream(platform)), null)

    @Test
    fun bothLoadConcurrently() =
        runTest {
            val started = mutableSetOf<Platform>()
            val gates = Platform.entries.associateWith { CompletableDeferred<Outcome<Page<Stream>>>() }
            platforms.forEach { fake ->
                fake.topLive = {
                    started += fake.id
                    gates.getValue(fake.id).await()
                }
            }

            val result = async { loadBoth(platforms) { it.topLive(null) } }
            runCurrent()

            assertEquals(setOf(Platform.TWITCH, Platform.KICK), started)
            assertFalse(result.isCompleted)

            gates.forEach { (platform, gate) -> gate.complete(Outcome.Success(page(platform))) }
            assertEquals(
                mapOf(
                    Platform.TWITCH to PlatformSlice.Loaded(page(Platform.TWITCH)),
                    Platform.KICK to PlatformSlice.Loaded(page(Platform.KICK)),
                ),
                result.await(),
            )
        }

    @Test
    fun oneFailingPlatformKeepsOther() =
        runTest {
            twitch.topLive = { Outcome.Failure(LurkError.Offline) }
            kick.topLive = { Outcome.Success(page(Platform.KICK)) }

            val result = loadBoth(platforms) { it.topLive(null) }

            assertEquals(PlatformSlice.Failed(LurkError.Offline), result[Platform.TWITCH])
            assertEquals(PlatformSlice.Loaded(page(Platform.KICK)), result[Platform.KICK])
        }

    @Test
    fun notSignedInMapsToNotSignedIn() =
        runTest {
            twitch.followed = { Outcome.Failure(LurkError.NotSignedIn) }
            kick.followed = { Outcome.Success(Followed(emptyList(), emptyList())) }

            val result = loadBoth(platforms) { it.followed() }

            assertEquals(PlatformSlice.NotSignedIn, result[Platform.TWITCH])
            assertEquals(PlatformSlice.Loaded(Followed(emptyList(), emptyList())), result[Platform.KICK])
        }

    @Test
    fun thrownLurkExceptionBecomesFailed() =
        runTest {
            kick.topLive = { throw LurkException(LurkError.Blocked) }

            val result = loadBoth(platforms) { it.topLive(null) }

            assertEquals(PlatformSlice.Failed(LurkError.Blocked), result[Platform.KICK])
            assertEquals(PlatformSlice.Loaded(Page(emptyList<Stream>(), null)), result[Platform.TWITCH])
        }

    @Test
    fun cancellationPropagates() =
        runTest {
            val cancelled = mutableSetOf<Platform>()
            platforms.forEach { fake ->
                fake.topLive = {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled += fake.id
                    }
                }
            }

            val job = launch { loadBoth(platforms) { it.topLive(null) } }
            runCurrent()
            job.cancel()
            runCurrent()

            assertTrue(job.isCancelled)
            assertEquals(setOf(Platform.TWITCH, Platform.KICK), cancelled)
        }

    @Test
    fun thrownCancellationIsRethrown() =
        runTest {
            kick.topLive = { throw CancellationException("stop") }

            assertFailsWith<CancellationException> { loadBoth(platforms) { it.topLive(null) } }
        }

    @Test
    fun onlyTheGivenPlatformsAreLoaded() =
        runTest {
            val result = loadBoth(listOf(kick)) { it.topLive(null) }

            assertEquals(setOf(Platform.KICK), result.keys)
            assertEquals(0, twitch.topLiveCalls)
            assertEquals(1, kick.topLiveCalls)
        }
}

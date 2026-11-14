package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TopLiveViewModelTest {
    private val harness = BrowseTestHarness()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(harness.dispatcher)
    }

    @AfterTest
    fun tearDown() {
        harness.store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(): TopLiveViewModel =
        harness.create(TopLiveViewModel::class) {
            TopLiveViewModel(listOf(twitch, kick), TestDispatchers(harness.dispatcher))
        }

    private fun s(
        platform: Platform,
        login: String,
        viewers: Long,
    ): Stream = testStream(platform, login = login, viewers = viewers, displayName = login)

    private fun List<Stream>.viewers() = map { it.viewers }

    /** Twitch with a first page of 900 viewers and a second of 800. */
    private fun twoTwitchPages() {
        twitch.topLive = { cursor ->
            if (cursor == null) {
                pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
            } else {
                pageOf(s(Platform.TWITCH, "t3", 800))
            }
        }
    }

    @Test
    fun topLiveMergedByViewers() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 300), s(Platform.TWITCH, "t2", 900)) }
            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            val state = watch(viewModel().state)

            assertEquals(listOf(900L, 500L, 300L), state.value.streams.viewers())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
            assertEquals(PlatformFilter.ALL, state.value.filter)
        }

    @Test
    fun loadMoreAppendsSortedBatchWithoutReshuffling() =
        runTest {
            twoTwitchPages()
            kick.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.KICK, "k1", 500), next = "k2")
                } else {
                    pageOf(s(Platform.KICK, "k3", 950))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)
            assertTrue(state.value.canLoadMore)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(900L, 500L, 950L, 800L), state.value.streams.viewers())
            assertFalse(state.value.canLoadMore)
            assertFalse(state.value.loadingMore)
        }

    @Test
    fun loadMoreSkipsPlatformWithoutCursor() =
        runTest {
            twoTwitchPages()
            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500), next = null) }
            val vm = viewModel()
            watch(vm.state)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(2, twitch.topLiveCalls)
            assertEquals(1, kick.topLiveCalls)
        }

    @Test
    fun loadMoreIgnoredWhileLoading() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            twitch.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    gate.await()
                    pageOf(s(Platform.TWITCH, "t3", 800))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertTrue(state.value.loadingMore)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(2, twitch.topLiveCalls)

            gate.complete(Unit)
            runCurrent()
            assertFalse(state.value.loadingMore)
        }

    @Test
    fun loadMoreIgnoredBeforeFirstPageLands() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            twitch.topLive = {
                gate.await()
                pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
            }
            val vm = viewModel()
            watch(vm.state)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(1, twitch.topLiveCalls)
        }

    @Test
    fun filterIsClientSideNoRefetch() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 900)) }
            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(TopLiveIntent.SelectFilter(PlatformFilter.KICK))
            runCurrent()

            assertEquals(listOf(Platform.KICK), state.value.streams.map { it.channel.platform })
            assertEquals(setOf(Platform.KICK), state.value.slices.keys)
            assertEquals(1, twitch.topLiveCalls)
            assertEquals(1, kick.topLiveCalls)
        }

    @Test
    fun loadMoreFailureKeepsItemsAndRecordsTheError() =
        runTest {
            twitch.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    Outcome.Failure(LurkError.Offline)
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(900L), state.value.streams.viewers())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
            assertEquals(mapOf(Platform.TWITCH to LurkError.Offline), state.value.loadMoreErrors)
            // The failed page waits for its Retry; nothing else can page.
            assertFalse(state.value.canLoadMore)
        }

    /** Kick pages on and on: each page has a next one. */
    private fun endlessKick() {
        kick.topLive = { cursor ->
            val n = cursor?.removePrefix("k")?.toInt() ?: 1
            pageOf(s(Platform.KICK, "k$n", 500L - n), next = "k${n + 1}")
        }
    }

    @Test
    fun aDeadEndOnOnePlatformLeavesTheOtherPaging() =
        runTest {
            twitch.topLive = { cursor ->
                if (cursor ==
                    null
                ) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    Outcome.Failure(LurkError.SchemaChanged)
                }
            }
            endlessKick()
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(mapOf(Platform.TWITCH to LurkError.SchemaChanged), state.value.loadMoreErrors)
            assertTrue(state.value.canLoadMore)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(2, twitch.topLiveCalls)
            assertEquals(3, kick.topLiveCalls)
            assertEquals(mapOf(Platform.TWITCH to LurkError.SchemaChanged), state.value.loadMoreErrors)
        }

    @Test
    fun aPageWaitingForItsRetryLeavesTheOtherPlatformPaging() =
        runTest {
            twitch.topLive = { cursor ->
                if (cursor ==
                    null
                ) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    Outcome.Failure(LurkError.Offline)
                }
            }
            endlessKick()
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(mapOf(Platform.TWITCH to LurkError.Offline), state.value.loadMoreErrors)
            assertTrue(state.value.canLoadMore)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            // Twitch's page waits for the Retry on its footer; only Kick asks for more.
            assertEquals(2, twitch.topLiveCalls)
            assertEquals(3, kick.topLiveCalls)
            assertEquals(mapOf(Platform.TWITCH to LurkError.Offline), state.value.loadMoreErrors)
        }

    @Test
    fun retryReloadsTheFailedPlatformOnly() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 900)) }
            kick.topLive = { Outcome.Failure(LurkError.Timeout) }
            val vm = viewModel()
            val state = watch(vm.state)
            assertEquals(PlatformSlice.Failed(LurkError.Timeout), state.value.slices[Platform.KICK])

            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            vm.onIntent(TopLiveIntent.Retry(Platform.KICK))
            runCurrent()

            assertEquals(listOf(900L, 500L), state.value.streams.viewers())
            assertEquals(1, twitch.topLiveCalls)
        }

    @Test
    fun refreshStartsOverFromTheFirstPage() =
        runTest {
            twoTwitchPages()
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(2, state.value.streams.size)

            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()

            assertEquals(listOf(900L), state.value.streams.viewers())
            assertEquals(3, twitch.topLiveCalls)
        }

    @Test
    fun aRetriedPageLandsBelowTheRowsAlreadyShown() =
        runTest {
            twoTwitchPages()
            var kickFails = true
            kick.topLive = { cursor ->
                when {
                    cursor == null -> pageOf(s(Platform.KICK, "k1", 500), next = "k2")
                    kickFails -> Outcome.Failure(LurkError.Offline)
                    else -> pageOf(s(Platform.KICK, "k3", 950))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(listOf(900L, 500L, 800L), state.value.streams.viewers())

            kickFails = false
            vm.onIntent(TopLiveIntent.Retry(Platform.KICK))
            runCurrent()

            assertEquals(listOf(900L, 500L, 800L, 950L), state.value.streams.viewers())
        }

    @Test
    fun aPlatformHiddenDuringLoadMoreAppendsBelowWhenShownAgain() =
        runTest {
            twoTwitchPages()
            kick.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.KICK, "k1", 500), next = "k2")
                } else {
                    pageOf(s(Platform.KICK, "k3", 950))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.SelectFilter(PlatformFilter.TWITCH))
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            vm.onIntent(TopLiveIntent.SelectFilter(PlatformFilter.ALL))
            runCurrent()
            assertEquals(listOf(900L, 500L, 800L), state.value.streams.viewers())

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(900L, 500L, 800L, 950L), state.value.streams.viewers())
        }

    @Test
    fun aStreamRepeatedOnALaterPageShowsOnce() =
        runTest {
            twitch.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    pageOf(s(Platform.TWITCH, "t1", 880), s(Platform.TWITCH, "t3", 800))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(900L, 800L), state.value.streams.viewers())
        }

    @Test
    fun refreshAllowsPagingAgainAfterANonRetryableStop() =
        runTest {
            var fail = true
            val cursors = mutableListOf<String?>()
            twitch.topLive = { cursor ->
                cursors += cursor
                when {
                    cursor == null -> pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                    fail -> Outcome.Failure(LurkError.IntegrityRequired)
                    else -> pageOf(s(Platform.TWITCH, "t2", 800))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(listOf<String?>(null, "t2"), cursors)

            fail = false
            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(900L, 800L), state.value.streams.viewers())
        }

    @Test
    fun aRetryKeepsTheFailedSliceOnScreenAndMarksItRetrying() =
        runTest {
            kick.topLive = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            val gate = CompletableDeferred<Unit>()
            kick.topLive = {
                gate.await()
                Outcome.Failure(LurkError.Offline)
            }

            vm.onIntent(TopLiveIntent.Retry(Platform.KICK))
            runCurrent()
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.KICK])
            assertEquals(setOf(Platform.KICK), state.value.retrying)

            gate.complete(Unit)
            runCurrent()
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.KICK])
            assertTrue(state.value.retrying.isEmpty())
        }

    @Test
    fun aRetryThatSucceedsReplacesTheFailure() =
        runTest {
            kick.topLive = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)

            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            vm.onIntent(TopLiveIntent.Retry(Platform.KICK))
            runCurrent()

            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.KICK])
            assertTrue(state.value.retrying.isEmpty())
        }

    @Test
    fun aLoadMoreRetryKeepsTheFooterErrorOnScreen() =
        runTest {
            var hold: CompletableDeferred<Unit>? = null
            twitch.topLive = { cursor ->
                if (cursor == null) {
                    pageOf(s(Platform.TWITCH, "t1", 900), next = "t2")
                } else {
                    hold?.await()
                    Outcome.Failure(LurkError.Offline)
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(TopLiveIntent.LoadMore)
            runCurrent()
            assertEquals(mapOf(Platform.TWITCH to LurkError.Offline), state.value.loadMoreErrors)

            val gate = CompletableDeferred<Unit>()
            hold = gate
            vm.onIntent(TopLiveIntent.Retry(Platform.TWITCH))
            runCurrent()

            assertEquals(mapOf(Platform.TWITCH to LurkError.Offline), state.value.loadMoreErrors)
            assertEquals(setOf(Platform.TWITCH), state.value.retrying)
            assertFalse(state.value.loadingMore)
            gate.complete(Unit)
            runCurrent()
            assertTrue(state.value.retrying.isEmpty())
        }

    @Test
    fun aRefreshDuringARetryClearsTheMark() =
        runTest {
            kick.topLive = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            kick.topLive = { CompletableDeferred<Outcome<io.github.kemurixd.lurk.core.model.Page<Stream>>>().await() }
            vm.onIntent(TopLiveIntent.Retry(Platform.KICK))
            runCurrent()
            assertEquals(setOf(Platform.KICK), state.value.retrying)

            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()

            assertTrue(state.value.retrying.isEmpty())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.KICK])
        }

    @Test
    fun aRefreshKeepsTheListOnScreenUntilTheNewPagesLand() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 900)) }
            kick.topLive = { pageOf(s(Platform.KICK, "k1", 500)) }
            val vm = viewModel()
            val state = watch(vm.state)
            val gate = CompletableDeferred<Unit>()
            twitch.topLive = {
                gate.await()
                pageOf(s(Platform.TWITCH, "t9", 990))
            }
            kick.topLive = {
                gate.await()
                pageOf(s(Platform.KICK, "k9", 400))
            }

            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()

            // No skeleton: the rows and the loaded slices stay while the pull spinner shows.
            assertEquals(listOf(900L, 500L), state.value.streams.viewers())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
            assertTrue(state.value.refreshing)
            assertTrue(state.value.retrying.isEmpty())

            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(990L, 400L), state.value.streams.viewers())
            assertFalse(state.value.refreshing)
        }

    @Test
    fun aRefreshKeepsAFailureOnScreenWithoutMarkingItRetrying() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 900)) }
            kick.topLive = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            val gate = CompletableDeferred<Unit>()
            kick.topLive = {
                gate.await()
                pageOf(s(Platform.KICK, "k1", 500))
            }

            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()

            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.KICK])
            assertTrue(state.value.retrying.isEmpty())
            assertTrue(state.value.refreshing)

            gate.complete(Unit)
            runCurrent()
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.KICK])
            assertEquals(listOf(900L, 500L), state.value.streams.viewers())
        }

    @Test
    fun aFailedRefreshKeepsTheRowsAndShowsTheFailure() =
        runTest {
            twitch.topLive = { pageOf(s(Platform.TWITCH, "t1", 900)) }
            val vm = viewModel()
            val state = watch(vm.state)

            twitch.topLive = { Outcome.Failure(LurkError.Timeout) }
            vm.onIntent(TopLiveIntent.Refresh)
            runCurrent()

            assertEquals(listOf(900L), state.value.streams.viewers())
            assertEquals(PlatformSlice.Failed(LurkError.Timeout), state.value.slices[Platform.TWITCH])
            assertFalse(state.value.refreshing)

            twitch.topLive = { pageOf(s(Platform.TWITCH, "t2", 950)) }
            vm.onIntent(TopLiveIntent.Retry(Platform.TWITCH))
            runCurrent()
            assertEquals(listOf(950L), state.value.streams.viewers())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
        }
}

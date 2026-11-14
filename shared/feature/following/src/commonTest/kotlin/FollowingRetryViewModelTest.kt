package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A failure stays on screen while it is asked again, by the user or by the minute reload. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowingRetryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val store = ViewModelStore()
    private val lastLive = LastLiveLoader(listOf(twitch, kick), TestDispatchers(dispatcher))
    private val fetchers = Platform.entries.associateWith { FakeProfileFetcher(it) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.accounts(): AccountsState {
        val sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
        Platform.entries.forEach { sessions.signIn(it, "test-token") }
        return AccountsState(sessions)
    }

    private fun viewModel(accounts: AccountsState): FollowingViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    FollowingViewModel(
                        listOf(twitch, kick),
                        accounts,
                        inMemorySettingsStore(),
                        TestDispatchers(dispatcher),
                        lastLive,
                    )
                }
            }
        return ViewModelProvider.create(store, factory)["vm", FollowingViewModel::class]
    }

    private fun followed(live: List<io.github.kemurixd.lurk.core.model.Stream> = emptyList()): Outcome<Followed> =
        Outcome.Success(Followed(live, emptyList()))

    private suspend fun ReceiveTurbine<FollowingUiState>.awaitUntil(
        predicate: (FollowingUiState) -> Boolean,
    ): FollowingUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private val FollowingUiState.allLoaded: Boolean
        get() = slices.values.all { it is PlatformSlice.Loaded<*> && !it.stale } && !refreshing

    @Test
    fun aRetryKeepsTheFailedSliceInPlaceAndMarksItRetrying() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                val gate = CompletableDeferred<Unit>()
                kick.followed = {
                    gate.await()
                    Outcome.Failure(LurkError.Offline)
                }

                vm.onIntent(FollowingIntent.Retry(Platform.KICK))

                val retrying = awaitUntil { it.refreshing }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), retrying.slices[Platform.KICK])
                assertEquals(setOf(Platform.KICK), retrying.retrying)
                gate.complete(Unit)
                val done = awaitUntil { !it.refreshing }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), done.slices[Platform.KICK])
                assertTrue(done.retrying.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRetryThatSucceedsReplacesTheFailure() =
        runTest {
            var kickResult: Outcome<Followed> = Outcome.Failure(LurkError.Offline)
            kick.followed = { kickResult }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                kickResult = followed(live = listOf(testStream(Platform.KICK, channelId = "")))

                vm.onIntent(FollowingIntent.Retry(Platform.KICK))

                val state = awaitUntil { it.allLoaded }
                assertTrue(state.retrying.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRetryOverStaleItemsKeepsTheBannerAndMarksItRetrying() =
        runTest {
            val stream = testStream(Platform.KICK, channelId = "")
            var kickResult: Outcome<Followed> = followed(live = listOf(stream))
            kick.followed = { kickResult }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                kickResult = Outcome.Failure(LurkError.Offline)
                vm.onIntent(FollowingIntent.Refresh)
                awaitUntil { !it.refreshing && it.refreshErrors.isNotEmpty() }
                val gate = CompletableDeferred<Unit>()
                kick.followed = {
                    gate.await()
                    Outcome.Failure(LurkError.Offline)
                }

                vm.onIntent(FollowingIntent.Retry(Platform.KICK))

                val retrying = awaitUntil { it.refreshing }
                assertEquals(mapOf(Platform.KICK to LurkError.Offline), retrying.refreshErrors)
                assertEquals(setOf(Platform.KICK), retrying.retrying)
                gate.complete(Unit)
                val done = awaitUntil { !it.refreshing }
                assertEquals(mapOf(Platform.KICK to LurkError.Offline), done.refreshErrors)
                assertTrue(done.retrying.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theTimedReloadNeverTurnsAFailureIntoASkeleton() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                advanceTimeBy(61.seconds)
                runCurrent()
                val seen = cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<FollowingUiState>>()
                assertTrue(seen.isNotEmpty())
                assertTrue(seen.all { it.value.slices[Platform.KICK] is PlatformSlice.Failed })
                assertTrue(seen.all { it.value.retrying.isEmpty() })
            }
            assertEquals(2, kick.followedCalls)
        }

    @Test
    fun aPullToRefreshOverABannerKeepsItAndShowsNoRetrySpinner() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                val gate = CompletableDeferred<Unit>()
                kick.followed = {
                    gate.await()
                    Outcome.Failure(LurkError.Offline)
                }

                vm.onIntent(FollowingIntent.Refresh)

                val pulling = awaitUntil { it.manualRefresh }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), pulling.slices[Platform.KICK])
                assertTrue(pulling.retrying.isEmpty())
                gate.complete(Unit)
                val done = awaitUntil { !it.refreshing }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), done.slices[Platform.KICK])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun signingOutDuringARetryClearsTheMark() =
        runTest {
            val sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
            Platform.entries.forEach { sessions.signIn(it, "test-token") }
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(AccountsState(sessions))

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                kick.followed = { CompletableDeferred<Outcome<Followed>>().await() }
                vm.onIntent(FollowingIntent.Retry(Platform.KICK))
                awaitUntil { it.retrying.isNotEmpty() }

                sessions.signOut(Platform.KICK)

                val state = awaitUntil { it.slices[Platform.KICK] == PlatformSlice.NotSignedIn && it.retrying.isEmpty() }
                assertTrue(state.retrying.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun comingBackWhileARetryRunsDoesNotStickTheMark() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(accounts())
            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                val gate = CompletableDeferred<Unit>()
                kick.followed = {
                    gate.await()
                    Outcome.Failure(LurkError.Offline)
                }
                vm.onIntent(FollowingIntent.Retry(Platform.KICK))
                awaitUntil { it.retrying.isNotEmpty() }
                cancelAndIgnoreRemainingEvents()
                gate.complete(Unit)
            }
            runCurrent()

            vm.state.test {
                val state = awaitUntil { !it.refreshing }
                assertTrue(state.retrying.isEmpty())
                assertEquals(PlatformSlice.Failed(LurkError.Offline), state.slices[Platform.KICK])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRetryDuringAPullAndAPullDuringARetryAreNoOps() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(accounts())
            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                kick.followed = { CompletableDeferred<Outcome<Followed>>().await() }
                vm.onIntent(FollowingIntent.Refresh)
                awaitUntil { it.manualRefresh }
                runCurrent()
                val calls = kick.followedCalls

                vm.onIntent(FollowingIntent.Retry(Platform.KICK))
                runCurrent()

                assertEquals(calls, kick.followedCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

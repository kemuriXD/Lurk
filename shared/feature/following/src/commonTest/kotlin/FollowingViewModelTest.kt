package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.FollowingSort
import io.github.kemurixd.lurk.core.storage.PlatformFilterSetting
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FollowingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val settings = inMemorySettingsStore()
    private val store = ViewModelStore()
    private var created = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.accounts(vararg signedIn: Platform = Platform.entries.toTypedArray()): AccountsState {
        val fetchers = Platform.entries.map { FakeProfileFetcher(it) }
        val sessions = SessionRepository(InMemorySecureStore(), fetchers, backgroundScope)
        signedIn.forEach { sessions.signIn(it, TOKEN) }
        return AccountsState(sessions)
    }

    /** Built through a [ViewModelStore] so [tearDown] clears it like the framework would. */
    private fun viewModel(accounts: AccountsState): FollowingViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    FollowingViewModel(
                        listOf(twitch, kick),
                        accounts,
                        settings,
                        TestDispatchers(dispatcher),
                    )
                }
            }
        return ViewModelProvider.create(store, factory)["vm${created++}", FollowingViewModel::class]
    }

    private fun followed(
        live: List<io.github.kemurixd.lurk.core.model.Stream> = emptyList(),
        offline: List<io.github.kemurixd.lurk.core.model.Channel> = emptyList(),
    ): Outcome<Followed> = Outcome.Success(Followed(live, offline))

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
    fun loadsBothAndMergesLiveByViewers() =
        runTest {
            val twitchBig = testStream(Platform.TWITCH, login = "big", viewers = 900)
            val twitchSmall = testStream(Platform.TWITCH, login = "small", viewers = 10)
            val kickMid = testStream(Platform.KICK, login = "mid", viewers = 500, channelId = "")
            twitch.followed = { followed(live = listOf(twitchSmall, twitchBig)) }
            kick.followed = { followed(live = listOf(kickMid)) }
            val vm = viewModel(accounts())

            vm.state.test {
                val state = awaitUntil { it.allLoaded }
                assertEquals(listOf(twitchBig, kickMid, twitchSmall), state.live)
                assertEquals(setOf(Platform.TWITCH, Platform.KICK), state.slices.keys)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun offlineSortedAtoZ() =
        runTest {
            twitch.followed = {
                followed(
                    offline =
                        listOf(
                            testChannel(Platform.TWITCH, login = "zed", displayName = "Zed"),
                            testChannel(Platform.TWITCH, login = "bea", displayName = "bea"),
                        ),
                )
            }
            kick.followed = {
                followed(offline = listOf(testChannel(Platform.KICK, login = "amy", id = "", displayName = "Amy")))
            }
            val vm = viewModel(accounts())

            vm.state.test {
                val state = awaitUntil { it.allLoaded }
                assertEquals(listOf("Amy", "bea", "Zed"), state.offline.map { it.displayName })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun recentSortNewestFirstNullsLast() =
        runTest {
            val old = testStream(Platform.TWITCH, login = "old", startedAt = Instant.fromEpochSeconds(1_000))
            val unknown = testStream(Platform.TWITCH, login = "unknown", viewers = 10_000, startedAt = null)
            val new =
                testStream(Platform.KICK, login = "new", channelId = "", startedAt = Instant.fromEpochSeconds(2_000))
            twitch.followed = { followed(live = listOf(unknown, old)) }
            kick.followed = { followed(live = listOf(new)) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                vm.onIntent(FollowingIntent.SelectSort(FollowingSort.RECENT))
                val state = awaitUntil { it.sort == FollowingSort.RECENT }
                assertEquals(listOf(new, old, unknown), state.live)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun filterKickHidesTwitch() =
        runTest {
            val kickStream = testStream(Platform.KICK, login = "kicker", channelId = "")
            twitch.followed = {
                followed(
                    live = listOf(testStream(Platform.TWITCH)),
                    offline = listOf(testChannel(Platform.TWITCH, login = "away")),
                )
            }
            kick.followed = { followed(live = listOf(kickStream)) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                vm.onIntent(FollowingIntent.SelectFilter(PlatformFilter.KICK))
                val state = awaitUntil { it.filter == PlatformFilter.KICK }
                assertEquals(listOf(kickStream), state.live)
                assertEquals(emptyList(), state.offline)
                assertEquals(setOf(Platform.KICK), state.slices.keys)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun filterAndSortPersisted() =
        runTest {
            val accounts = accounts()
            val vm = viewModel(accounts)

            vm.state.test {
                awaitUntil { it.allLoaded }
                vm.onIntent(FollowingIntent.SelectFilter(PlatformFilter.KICK))
                vm.onIntent(FollowingIntent.SelectSort(FollowingSort.NAME))
                awaitUntil { it.filter == PlatformFilter.KICK && it.sort == FollowingSort.NAME }
                cancelAndIgnoreRemainingEvents()
            }

            val stored = settings.settings.first()
            assertEquals(PlatformFilterSetting.KICK, stored.followingFilter)
            assertEquals(FollowingSort.NAME, stored.followingSort)

            viewModel(accounts).state.test {
                val state = awaitUntil { it.allLoaded }
                assertEquals(PlatformFilter.KICK, state.filter)
                assertEquals(FollowingSort.NAME, state.sort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun signedOutPlatformIsNotSignedIn() =
        runTest {
            twitch.followed = { followed(live = listOf(testStream(Platform.TWITCH))) }
            val vm = viewModel(accounts(Platform.TWITCH))

            vm.state.test {
                val state = awaitUntil { it.slices[Platform.TWITCH] is PlatformSlice.Loaded && !it.refreshing }
                assertEquals(PlatformSlice.NotSignedIn, state.slices[Platform.KICK])
                assertEquals(listOf(testStream(Platform.TWITCH)), state.live)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, kick.followedCalls)
        }

    @Test
    fun expiredPlatformKeepsOtherLoaded() =
        runTest {
            twitch.followed = { followed(live = listOf(testStream(Platform.TWITCH))) }
            kick.followed = { Outcome.Failure(LurkError.AuthExpired) }
            val vm = viewModel(accounts())

            vm.state.test {
                val state = awaitUntil { !it.refreshing && it.slices.values.none { s -> s is PlatformSlice.Loading } }
                assertEquals(PlatformSlice.Failed(LurkError.AuthExpired), state.slices[Platform.KICK])
                assertEquals(
                    PlatformSlice.Loaded(Followed(listOf(testStream(Platform.TWITCH)), emptyList())),
                    state.slices[Platform.TWITCH],
                )
                assertEquals(listOf(testStream(Platform.TWITCH)), state.live)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshesEvery60s() =
        runTest {
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                assertEquals(1, twitch.followedCalls)
                assertEquals(1, kick.followedCalls)

                advanceTimeBy(60.seconds)
                runCurrent()

                assertEquals(2, twitch.followedCalls)
                assertEquals(2, kick.followedCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshStopsWhenUnsubscribed() =
        runTest {
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(5.minutes)
            runCurrent()

            assertEquals(1, twitch.followedCalls)
            assertEquals(1, kick.followedCalls)
        }

    @Test
    fun refreshNotDoubledWhileInFlight() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            twitch.followed = {
                gate.await()
                followed()
            }
            kick.followed = {
                gate.await()
                followed()
            }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.refreshing }
                vm.onIntent(FollowingIntent.Refresh)
                vm.onIntent(FollowingIntent.Refresh)
                runCurrent()

                assertEquals(1, twitch.followedCalls)
                assertEquals(1, kick.followedCalls)

                gate.complete(Unit)
                awaitUntil { it.allLoaded }
                assertEquals(1, twitch.followedCalls)
                assertEquals(1, kick.followedCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun retryReloadsOnlyThatPlatform() =
        runTest {
            var kickResult: Outcome<Followed> = Outcome.Failure(LurkError.Offline)
            kick.followed = { kickResult }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.slices[Platform.KICK] is PlatformSlice.Failed && !it.refreshing }
                kickResult = followed(live = listOf(testStream(Platform.KICK, channelId = "")))

                vm.onIntent(FollowingIntent.Retry(Platform.KICK))

                val state = awaitUntil { it.allLoaded }
                assertEquals(listOf(testStream(Platform.KICK, channelId = "")), state.live)
                assertEquals(1, twitch.followedCalls)
                assertEquals(2, kick.followedCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun failedRefreshKeepsPreviousItemsMarkedStale() =
        runTest {
            val stream = testStream(Platform.KICK, channelId = "")
            var kickResult: Outcome<Followed> = followed(live = listOf(stream))
            kick.followed = { kickResult }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                kickResult = Outcome.Failure(LurkError.Offline)

                vm.onIntent(FollowingIntent.Refresh)

                val state = awaitUntil { !it.refreshing && it.refreshErrors.isNotEmpty() }
                assertEquals(
                    PlatformSlice.Loaded(Followed(listOf(stream), emptyList()), stale = true),
                    state.slices[Platform.KICK],
                )
                assertEquals(mapOf(Platform.KICK to LurkError.Offline), state.refreshErrors)
                assertEquals(listOf(stream), state.live)
                assertFalse(state.slices.getValue(Platform.TWITCH).let { it is PlatformSlice.Loaded<*> && it.stale })

                kickResult = followed(live = listOf(stream))
                vm.onIntent(FollowingIntent.Refresh)
                val recovered = awaitUntil { it.allLoaded }
                assertTrue(recovered.refreshErrors.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val TOKEN = "test-token"
    }
}

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
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.FollowingSortSetting
import io.github.kemurixd.lurk.core.storage.PlatformFilterSetting
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.feature.testing.testAccount
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    private lateinit var sessions: SessionRepository
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

    private suspend fun TestScope.accounts(vararg signedIn: Platform = Platform.entries.toTypedArray()): AccountsState {
        sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
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
                        lastLive,
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
            assertEquals(FollowingSortSetting.NAME, stored.followingSort)

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

    @Test
    fun signOutDuringLoadShowsNotSignedInAtOnce() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val kickStream = testStream(Platform.KICK, login = "old-account-follow", channelId = "")
            kick.followed = {
                gate.await()
                followed(live = listOf(kickStream))
            }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.refreshing && it.slices[Platform.TWITCH] is PlatformSlice.Loaded<*> }

                sessions.signOut(Platform.KICK)
                val signedOut = awaitUntil { it.slices[Platform.KICK] == PlatformSlice.NotSignedIn }
                assertFalse(signedOut.refreshing)
                assertTrue(Platform.KICK !in signedOut.refreshErrors)

                gate.complete(Unit)
                runCurrent()

                val latest = vm.state.value
                assertEquals(PlatformSlice.NotSignedIn, latest.slices[Platform.KICK])
                assertTrue(kickStream !in latest.live)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resubscribeAfterStopReloadsOnceAndResumes() =
        runTest {
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(6.seconds)
            runCurrent()
            assertEquals(1, twitch.followedCalls)

            vm.state.test {
                runCurrent()
                assertEquals(2, twitch.followedCalls)
                assertEquals(2, kick.followedCalls)

                advanceTimeBy(60.seconds)
                runCurrent()
                assertEquals(3, twitch.followedCalls)
                assertEquals(3, kick.followedCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Signs Kick out, then in again as a second stand-in account. */
    private suspend fun switchKickToSecondAccount() {
        sessions.signOut(Platform.KICK)
        fetchers.getValue(Platform.KICK).result = Outcome.Success(SECOND_KICK_ACCOUNT)
        sessions.signIn(Platform.KICK, TOKEN)
    }

    @Test
    fun accountSwitchMidLoadKeepsRefreshingUntilTheNewAccountLoads() =
        runTest {
            val firstGate = CompletableDeferred<Unit>()
            val secondGate = CompletableDeferred<Unit>()
            val firstStream = testStream(Platform.KICK, login = "first-follow", channelId = "")
            val secondStream = testStream(Platform.KICK, login = "second-follow", channelId = "")
            kick.followed = {
                if (kick.followedCalls == 1) {
                    firstGate.await()
                    followed(live = listOf(firstStream))
                } else {
                    secondGate.await()
                    followed(live = listOf(secondStream))
                }
            }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.refreshing && it.slices[Platform.TWITCH] is PlatformSlice.Loaded<*> }

                switchKickToSecondAccount()
                runCurrent()
                firstGate.complete(Unit)
                runCurrent()

                assertEquals(2, kick.followedCalls)
                val switching = vm.state.value
                assertTrue(switching.refreshing)
                assertEquals(PlatformSlice.Loading, switching.slices[Platform.KICK])
                assertTrue(firstStream !in switching.live)

                secondGate.complete(Unit)
                val loaded = awaitUntil { it.allLoaded }
                assertEquals(listOf(secondStream), loaded.live.filter { it.channel.platform == Platform.KICK })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun accountSwitchWhileUnsubscribedNeverShowsThePreviousAccount() =
        runTest {
            val firstStream = testStream(Platform.KICK, login = "first-follow", channelId = "")
            val secondStream = testStream(Platform.KICK, login = "second-follow", channelId = "")
            kick.followed = { followed(live = listOf(firstStream)) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded && firstStream in it.live }
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(6.seconds)
            runCurrent()

            switchKickToSecondAccount()
            kick.followed = { followed(live = listOf(secondStream)) }

            vm.state.test {
                while (true) {
                    val state = awaitItem()
                    assertTrue(firstStream !in state.live, "the previous account's follows were shown")
                    if (state.allLoaded) {
                        assertTrue(secondStream in state.live)
                        break
                    }
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val TOKEN = "test-token"
        val SECOND_KICK_ACCOUNT = testAccount(Platform.KICK, "streamer2", "Streamer Two", "1000002")
    }

    @Test
    fun anOfflineKickRowOnScreenGetsItsLastLiveTime() =
        runTest {
            val then = Instant.parse("2026-01-01T00:00:00Z")
            val shown = testChannel(Platform.KICK, login = "amy", id = "", displayName = "Amy")
            val hidden = testChannel(Platform.KICK, login = "bob", id = "", displayName = "Bob")
            kick.followed = { followed(offline = listOf(shown, hidden)) }
            kick.lastLive = { Outcome.Success(then) }
            val vm = viewModel(accounts())

            vm.state.test {
                val loaded = awaitUntil { it.allLoaded }
                assertTrue(loaded.offline.all { it.lastStream?.startedAt == null })
                vm.onIntent(FollowingIntent.OfflineRowShown(shown.ref))
                val filled = awaitUntil { state -> state.offline.any { it.lastStream?.startedAt != null } }
                assertEquals(
                    then,
                    filled.offline
                        .single { it.ref == shown.ref }
                        .lastStream
                        ?.startedAt,
                )
                assertEquals(
                    null,
                    filled.offline
                        .single { it.ref == hidden.ref }
                        .lastStream
                        ?.startedAt,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(shown.ref), kick.lastLiveRequests)
        }

    @Test
    fun aRowHiddenBeforeItsTurnCostsNoLookup() =
        runTest {
            val amy = testChannel(Platform.KICK, login = "amy", id = "", displayName = "Amy")
            kick.followed = { followed(offline = listOf(amy)) }
            kick.lastLive = {
                delay(1_000)
                Outcome.Success(Instant.parse("2026-01-01T00:00:00Z"))
            }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                // Three rows elsewhere hold every permit, so amy has to wait her turn.
                repeat(3) { vm.onIntent(FollowingIntent.OfflineRowShown(ChannelRef(Platform.KICK, "busy$it"))) }
                vm.onIntent(FollowingIntent.OfflineRowShown(amy.ref))
                runCurrent()
                vm.onIntent(FollowingIntent.OfflineRowHidden(amy.ref))
                advanceTimeBy(5.seconds)
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(amy.ref in kick.lastLiveRequests, "fetched: ${kick.lastLiveRequests}")
        }

    @Test
    fun aFailedLastLiveLookupKeepsTheRowOffline() =
        runTest {
            val amy = testChannel(Platform.KICK, login = "amy", id = "", displayName = "Amy")
            kick.followed = { followed(offline = listOf(amy)) }
            kick.lastLive = { Outcome.Failure(LurkError.Timeout) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                vm.onIntent(FollowingIntent.OfflineRowShown(amy.ref))
                runCurrent()
                advanceTimeBy(1.seconds)
                assertEquals(
                    null,
                    vm.state.value.offline
                        .single()
                        .lastStream
                        ?.startedAt,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(amy.ref), kick.lastLiveRequests)
        }

    @Test
    fun twitchRowsNeverCostALastLiveLookup() =
        runTest {
            val zed = testChannel(Platform.TWITCH, login = "zed", displayName = "Zed")
            twitch.followed = { followed(offline = listOf(zed)) }
            val vm = viewModel(accounts())

            vm.state.test {
                awaitUntil { it.allLoaded }
                vm.onIntent(FollowingIntent.OfflineRowShown(zed.ref))
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(twitch.lastLiveRequests.isEmpty())
        }
}

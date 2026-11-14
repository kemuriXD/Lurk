package io.github.kemurixd.lurk.feature.channel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val store = ViewModelStore()
    private var created = 0
    private lateinit var sessions: SessionRepository
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

    private suspend fun TestScope.accounts(vararg signedIn: Platform): AccountsState {
        sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
        signedIn.forEach { sessions.signIn(it, TOKEN) }
        return AccountsState(sessions)
    }

    private fun viewModel(
        platform: FakeStreamingPlatform,
        accounts: AccountsState,
        login: String = "streamer1",
    ): ChannelViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    ChannelViewModel(platform, ChannelRef(platform.id, login), accounts, TestDispatchers(dispatcher))
                }
            }
        return ViewModelProvider.create(store, factory)["vm${created++}", ChannelViewModel::class]
    }

    private suspend fun ReceiveTurbine<ChannelUiState>.awaitUntil(
        predicate: (ChannelUiState) -> Boolean,
    ): ChannelUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private fun ChannelUiState.loaded(): Boolean = load is PlatformSlice.Loaded<*>

    private fun TestScope.keepCollecting(vm: ChannelViewModel) {
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
    }

    @Test
    fun loadsChannelByRef() =
        runTest {
            val loaded = testChannel(Platform.TWITCH, displayName = "Streamer One")
            var asked: ChannelRef? = null
            twitch.channel = {
                asked = it
                Outcome.Success(loaded)
            }
            val vm = viewModel(twitch, accounts())

            vm.state.test {
                assertEquals(PlatformSlice.Loading, awaitItem().load)
                assertEquals(PlatformSlice.Loaded(loaded), awaitUntil { it.loaded() }.load)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(ChannelRef(Platform.TWITCH, "streamer1"), asked)
        }

    @Test
    fun loadFailureShowsErrorAndRetryReloads() =
        runTest {
            twitch.channel = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(twitch, accounts())

            vm.state.test {
                assertEquals(
                    PlatformSlice.Failed(LurkError.Offline),
                    awaitUntil { it.load is PlatformSlice.Failed }.load,
                )
                twitch.channel = { Outcome.Success(testChannel(Platform.TWITCH)) }
                vm.onIntent(ChannelIntent.Retry)
                awaitUntil { it.loaded() }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, twitch.channelCalls)
        }

    @Test
    fun followHiddenWhenSignedOut() =
        runTest {
            val vm = viewModel(kick, accounts())

            vm.state.test {
                assertEquals(FollowState.Hidden, awaitUntil { it.loaded() }.follow)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, kick.followedCalls)
        }

    @Test
    fun followingWhenInFollowedList() =
        runTest {
            // Kick followed channels have blank IDs, so the match is by platform and login.
            kick.followed = { Outcome.Success(Followed(emptyList(), listOf(testChannel(Platform.KICK, id = "")))) }
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                assertEquals(FollowState.Following, awaitUntil { it.follow == FollowState.Following }.follow)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun followingWhenLiveInFollowedList() =
        runTest {
            twitch.followed =
                { Outcome.Success(Followed(listOf(testStream(Platform.TWITCH, login = "Streamer1")), emptyList())) }
            val vm = viewModel(twitch, accounts(Platform.TWITCH))

            vm.state.test {
                assertEquals(FollowState.Following, awaitUntil { it.follow == FollowState.Following }.follow)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun notFollowingWhenAbsentFromFollowedList() =
        runTest {
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                awaitUntil { it.follow == FollowState.NotFollowing }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun followedFailureLeavesFollowUnknown() =
        runTest {
            kick.followed = { Outcome.Failure(LurkError.Timeout) }
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                assertEquals(
                    FollowState.Unknown,
                    awaitUntil { it.loaded() && it.follow != FollowState.Checking }.follow,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(kick.followedCalls >= 1)
        }

    @Test
    fun twitchFollowOpensTwitchTv() =
        runTest {
            val vm = viewModel(twitch, accounts(Platform.TWITCH))
            keepCollecting(vm)

            vm.effects.test {
                vm.onIntent(ChannelIntent.ToggleFollow)
                assertEquals(ChannelEffect.OpenExternal("https://www.twitch.tv/streamer1"), awaitItem())
            }
            assertEquals(0, twitch.setFollowCalls)
        }

    @Test
    fun kickFollowCallsSetFollowWithResolvedChannel() =
        runTest {
            val resolved = testChannel(Platform.KICK, id = "1000001")
            kick.channel = { Outcome.Success(resolved) }
            var sent: Pair<Channel, Boolean>? = null
            kick.setFollow = { channel, follow ->
                sent = channel to follow
                Outcome.Success(Unit)
            }
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                awaitUntil { it.loaded() && it.follow == FollowState.NotFollowing }
                vm.onIntent(ChannelIntent.ToggleFollow)
                assertEquals(FollowState.Busy(true), awaitItem().follow)
                assertEquals(FollowState.Following, awaitItem().follow)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(resolved to true, sent)
        }

    @Test
    fun kickUnfollowTogglesBack() =
        runTest {
            kick.followed = { Outcome.Success(Followed(emptyList(), listOf(testChannel(Platform.KICK, id = "")))) }
            var target: Boolean? = null
            kick.setFollow = { _, follow ->
                target = follow
                Outcome.Success(Unit)
            }
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                awaitUntil { it.loaded() && it.follow == FollowState.Following }
                vm.onIntent(ChannelIntent.ToggleFollow)
                assertEquals(FollowState.Busy(false), awaitItem().follow)
                assertEquals(FollowState.NotFollowing, awaitItem().follow)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, target)
        }

    @Test
    fun kickFollowFailureRollsBack() =
        runTest {
            kick.setFollow = { _, _ -> Outcome.Failure(LurkError.Blocked) }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)

            vm.effects.test {
                vm.onIntent(ChannelIntent.ToggleFollow)
                assertEquals(ChannelEffect.ShowError(LurkError.Blocked, follow = true), awaitItem())
            }
            runCurrent()
            assertEquals(FollowState.NotFollowing, vm.state.value.follow)
        }

    @Test
    fun kickUnfollowFailureSaysItWasAnUnfollow() =
        runTest {
            kick.followed = { Outcome.Success(Followed(emptyList(), listOf(testChannel(Platform.KICK, id = "")))) }
            kick.setFollow = { _, _ -> Outcome.Failure(LurkError.Blocked) }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)
            runCurrent()
            assertEquals(FollowState.Following, vm.state.value.follow)

            vm.effects.test {
                vm.onIntent(ChannelIntent.ToggleFollow)
                assertEquals(ChannelEffect.ShowError(LurkError.Blocked, follow = false), awaitItem())
            }
        }

    @Test
    fun followToggleIgnoredWhileInFlight() =
        runTest {
            val gate = CompletableDeferred<Outcome<Unit>>()
            kick.setFollow = { _, _ -> gate.await() }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)

            vm.onIntent(ChannelIntent.ToggleFollow)
            runCurrent()
            vm.onIntent(ChannelIntent.ToggleFollow)
            runCurrent()
            assertEquals(1, kick.setFollowCalls)
            assertEquals(FollowState.Busy(true), vm.state.value.follow)

            gate.complete(Outcome.Success(Unit))
            runCurrent()
            assertEquals(FollowState.Following, vm.state.value.follow)
            assertEquals(1, kick.setFollowCalls)
        }

    @Test
    fun signingOutHidesFollowAtOnce() =
        runTest {
            val vm = viewModel(kick, accounts(Platform.KICK))

            vm.state.test {
                awaitUntil { it.follow == FollowState.NotFollowing }
                sessions.signOut(Platform.KICK)
                awaitUntil { it.follow == FollowState.Hidden }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun lateSetFollowAfterSignOutIsIgnored() =
        runTest {
            val gate = CompletableDeferred<Outcome<Unit>>()
            kick.setFollow = { _, _ -> gate.await() }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)
            vm.onIntent(ChannelIntent.ToggleFollow)
            runCurrent()

            sessions.signOut(Platform.KICK)
            runCurrent()
            gate.complete(Outcome.Failure(LurkError.Blocked))
            runCurrent()

            assertEquals(FollowState.Hidden, vm.state.value.follow)
            vm.effects.test { expectNoEvents() }
        }

    @Test
    fun effectDeliveredOnce() =
        runTest {
            val vm = viewModel(twitch, accounts(Platform.TWITCH))
            keepCollecting(vm)

            vm.effects.test {
                vm.onIntent(ChannelIntent.Share)
                assertEquals(ChannelEffect.ShareText("https://www.twitch.tv/streamer1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test { expectNoEvents() }
        }

    @Test
    fun tapBeforeFollowedCompletesIsIgnored() =
        runTest {
            val gate = CompletableDeferred<Outcome<Followed>>()
            kick.followed = { gate.await() }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)

            assertEquals(FollowState.Checking, vm.state.value.follow)
            vm.onIntent(ChannelIntent.ToggleFollow)
            runCurrent()
            assertEquals(0, kick.setFollowCalls)
            assertEquals(FollowState.Checking, vm.state.value.follow)

            gate.complete(Outcome.Success(Followed(emptyList(), listOf(testChannel(Platform.KICK, id = "")))))
            runCurrent()
            assertEquals(FollowState.Following, vm.state.value.follow)
        }

    @Test
    fun failedToggleReloadsTheFollowList() =
        runTest {
            var calls = 0
            kick.followed = {
                calls++
                if (calls == 1) {
                    Outcome.Failure(LurkError.Timeout)
                } else {
                    Outcome.Success(Followed(emptyList(), listOf(testChannel(Platform.KICK, id = ""))))
                }
            }
            kick.setFollow = { _, _ -> Outcome.Failure(LurkError.Blocked) }
            val vm = viewModel(kick, accounts(Platform.KICK))
            keepCollecting(vm)
            assertEquals(FollowState.Unknown, vm.state.value.follow)

            vm.onIntent(ChannelIntent.ToggleFollow)
            runCurrent()

            assertEquals(2, kick.followedCalls)
            assertEquals(FollowState.Following, vm.state.value.follow)
        }

    @Test
    fun effectSentWithNoCollectorIsDeliveredOnceToTheNextOne() =
        runTest {
            val vm = viewModel(twitch, accounts(Platform.TWITCH))
            keepCollecting(vm)
            vm.onIntent(ChannelIntent.Share)
            runCurrent()

            vm.effects.test {
                assertEquals(ChannelEffect.ShareText("https://www.twitch.tv/streamer1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test { expectNoEvents() }
        }

    @Test
    fun watchLiveEmitsOpenPlayer() =
        runTest {
            val stream = testStream(Platform.TWITCH)
            twitch.channel = { Outcome.Success(testChannel(Platform.TWITCH, live = stream)) }
            val vm = viewModel(twitch, accounts())
            keepCollecting(vm)

            vm.effects.test {
                vm.onIntent(ChannelIntent.WatchLive)
                assertEquals(ChannelEffect.OpenPlayer(stream), awaitItem())
            }
        }

    @Test
    fun watchLiveDoesNothingWhileOffline() =
        runTest {
            val vm = viewModel(twitch, accounts())
            keepCollecting(vm)

            vm.onIntent(ChannelIntent.WatchLive)
            vm.effects.test { expectNoEvents() }
            assertIs<PlatformSlice.Loaded<*>>(vm.state.value.load)
        }

    @Test
    fun channelUrlsArePublicSiteUrls() {
        assertEquals("https://www.twitch.tv/streamer1", channelUrl(ChannelRef(Platform.TWITCH, "streamer1")))
        assertEquals("https://kick.com/streamer1", channelUrl(ChannelRef(Platform.KICK, "streamer1")))
    }

    private companion object {
        const val TOKEN = "stand-in-token"
    }

    @Test
    fun aRetryKeepsTheFailureOnScreenAndMarksItRetrying() =
        runTest {
            twitch.channel = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(twitch, accounts())

            vm.state.test {
                awaitUntil { it.load is PlatformSlice.Failed }
                val gate = CompletableDeferred<Unit>()
                twitch.channel = {
                    gate.await()
                    Outcome.Failure(LurkError.Offline)
                }

                vm.onIntent(ChannelIntent.Retry)

                val retrying = awaitUntil { it.retrying }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), retrying.load)
                gate.complete(Unit)
                val done = awaitUntil { !it.retrying }
                assertEquals(PlatformSlice.Failed(LurkError.Offline), done.load)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRetryThatSucceedsShowsTheChannel() =
        runTest {
            twitch.channel = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel(twitch, accounts())

            vm.state.test {
                awaitUntil { it.load is PlatformSlice.Failed }
                twitch.channel = { Outcome.Success(testChannel(Platform.TWITCH)) }

                vm.onIntent(ChannelIntent.Retry)

                val state = awaitUntil { it.loaded() }
                assertTrue(!state.retrying)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

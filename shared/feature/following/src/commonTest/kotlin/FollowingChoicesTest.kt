package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.SessionRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Following choices the user makes once and keeps: filter, sort and the folded Offline section. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowingChoicesTest {
    private val dispatcher = StandardTestDispatcher()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val settings = inMemorySettingsStore()
    private val store = ViewModelStore()
    private var created = 0
    private val lastLive = LastLiveLoader(listOf(twitch, kick), TestDispatchers(dispatcher))
    private val fetchers = Platform.entries.map { FakeProfileFetcher(it) }

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
        val sessions = SessionRepository(InMemorySecureStore(), fetchers, backgroundScope)
        Platform.entries.forEach { sessions.signIn(it, "test-token") }
        return AccountsState(sessions)
    }

    private fun viewModel(accounts: AccountsState): FollowingViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    FollowingViewModel(listOf(twitch, kick), accounts, settings, TestDispatchers(dispatcher), lastLive)
                }
            }
        return ViewModelProvider.create(store, factory)["vm${created++}", FollowingViewModel::class]
    }

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
    fun offlineFoldPersisted() =
        runTest {
            val accounts = accounts()
            val vm = viewModel(accounts)

            vm.state.test {
                assertFalse(awaitUntil { it.allLoaded }.offlineCollapsed)
                vm.onIntent(FollowingIntent.ToggleOffline)
                awaitUntil { it.offlineCollapsed }
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(settings.settings.first().followingOfflineCollapsed)

            val again = viewModel(accounts)
            again.state.test {
                assertTrue(awaitUntil { it.allLoaded }.offlineCollapsed)
                again.onIntent(FollowingIntent.ToggleOffline)
                awaitUntil { !it.offlineCollapsed }
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(settings.settings.first().followingOfflineCollapsed)
        }
}

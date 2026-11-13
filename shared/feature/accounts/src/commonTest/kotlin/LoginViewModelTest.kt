package io.github.kemurixd.lurk.feature.accounts

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var created = 0
    private val fetchers = Platform.entries.associateWith { FakeProfileFetcher(it) }
    private lateinit var sessions: SessionRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(platform: Platform = Platform.TWITCH): LoginViewModel {
        sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
        val factory =
            viewModelFactory {
                initializer { LoginViewModel(platform, sessions, TestDispatchers(dispatcher)) }
            }
        return ViewModelProvider.create(store, factory)["vm${created++}", LoginViewModel::class]
    }

    @Test
    fun startsLoadingThenReadyOncePageLoads() =
        runTest {
            val vm = viewModel()
            assertEquals(LoginUiState.Loading, vm.state.value)

            vm.onIntent(LoginIntent.PageLoaded)

            assertEquals(LoginUiState.Ready, vm.state.value)
        }

    @Test
    fun signInCalledOnceForRepeatedCookie() =
        runTest {
            val vm = viewModel()
            vm.onIntent(LoginIntent.PageLoaded)

            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            assertEquals(LoginUiState.SigningIn, vm.state.value)
            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            runCurrent()

            assertEquals(1, fetchers.getValue(Platform.TWITCH).calls)
        }

    @Test
    fun signInSuccessClearsCookiesThenDone() =
        runTest {
            val vm = viewModel(Platform.KICK)

            vm.effects.test {
                vm.onIntent(LoginIntent.CookieFound(COOKIE))
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.Done, awaitItem())
            }
            assertIs<AccountState.SignedIn>(sessions.state(Platform.KICK).value)
            assertEquals(AccountState.SignedOut, sessions.state(Platform.TWITCH).value)
            assertEquals(0, fetchers.getValue(Platform.TWITCH).calls)
        }

    @Test
    fun signInFailureKeepsLoginOpen() =
        runTest {
            fetchers.getValue(Platform.TWITCH).result = Outcome.Failure(LurkError.Offline)
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(LoginIntent.CookieFound(COOKIE))
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                runCurrent()
                expectNoEvents()
            }
            assertEquals(LoginUiState.Failed(LurkError.Offline), vm.state.value)
            assertEquals(AccountState.SignedOut, sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun anotherCookieAfterFailureTriesAgain() =
        runTest {
            val fetcher = fetchers.getValue(Platform.TWITCH)
            fetcher.result = Outcome.Failure(LurkError.Offline)
            val vm = viewModel()
            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            runCurrent()
            assertEquals(LoginUiState.Failed(LurkError.Offline), vm.state.value)

            fetcher.result = Outcome.Success(testAccount(Platform.TWITCH))
            vm.effects.test {
                vm.onIntent(LoginIntent.CookieFound(COOKIE))
                assertEquals(LoginUiState.SigningIn, vm.state.value)
                // The failure's ClearCookies is still queued, then this attempt's pair follows.
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.Done, awaitItem())
            }
            assertEquals(2, fetcher.calls)
        }

    @Test
    fun pageLoadAfterFailureKeepsTheError() =
        runTest {
            fetchers.getValue(Platform.TWITCH).result = Outcome.Failure(LurkError.Offline)
            val vm = viewModel()
            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            runCurrent()

            vm.onIntent(LoginIntent.PageLoaded)

            assertEquals(LoginUiState.Failed(LurkError.Offline), vm.state.value)
        }

    @Test
    fun cancelReturnsWithoutSignIn() =
        runTest {
            val vm = viewModel()
            vm.onIntent(LoginIntent.PageLoaded)

            vm.effects.test {
                vm.onIntent(LoginIntent.Cancel)
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.Done, awaitItem())
            }
            assertEquals(0, fetchers.getValue(Platform.TWITCH).calls)
            assertEquals(AccountState.SignedOut, sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun cancelWhileSigningInLetsItFinishAndDropsTheResult() =
        runTest {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(LoginIntent.CookieFound(COOKIE))
                vm.onIntent(LoginIntent.Cancel)
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.Done, awaitItem())
                runCurrent()
                expectNoEvents()
            }
            // Cancel no longer interrupts the session write: the sign-in runs to the end, whole, and its result is
            // ignored.
            assertEquals(1, fetchers.getValue(Platform.TWITCH).calls)
            assertIs<AccountState.SignedIn>(sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun failureAfterCancelChangesNothing() =
        runTest {
            fetchers.getValue(Platform.TWITCH).result = Outcome.Failure(LurkError.Offline)
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(LoginIntent.CookieFound(COOKIE))
                vm.onIntent(LoginIntent.Cancel)
                assertEquals(LoginEffect.ClearCookies, awaitItem())
                assertEquals(LoginEffect.Done, awaitItem())
                runCurrent()
                expectNoEvents()
            }
            assertEquals(1, fetchers.getValue(Platform.TWITCH).calls)
            assertFalse(vm.state.value is LoginUiState.Failed)
        }

    @Test
    fun cookieFoundAfterDoneIsIgnored() =
        runTest {
            val vm = viewModel()
            vm.onIntent(LoginIntent.Cancel)

            vm.onIntent(LoginIntent.CookieFound(COOKIE))
            runCurrent()

            assertEquals(0, fetchers.getValue(Platform.TWITCH).calls)
        }

    @Test
    fun cookieIntentHidesItsValue() {
        assertFalse(COOKIE in LoginIntent.CookieFound(COOKIE).toString())
    }

    private companion object {
        const val COOKIE = "test-cookie"
    }
}

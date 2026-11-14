package io.github.kemurixd.lurk.feature.accounts

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.AccountsState
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
class AccountsViewModelTest {
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

    private suspend fun TestScope.viewModel(vararg signedIn: Platform): AccountsViewModel {
        sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), backgroundScope)
        signedIn.forEach { sessions.signIn(it, COOKIE) }
        val accounts = AccountsState(sessions)
        val factory =
            viewModelFactory {
                initializer { AccountsViewModel(accounts, sessions, TestDispatchers(dispatcher)) }
            }
        return ViewModelProvider.create(store, factory)["vm${created++}", AccountsViewModel::class]
    }

    private suspend fun ReceiveTurbine<AccountsUiState>.awaitUntil(
        predicate: (AccountsUiState) -> Boolean,
    ): AccountsUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private fun AccountsUiState.status(platform: Platform): AccountStatus =
        rows.single { it.platform == platform }.status

    private suspend fun expire(platform: Platform) {
        fetchers.getValue(platform).result = Outcome.Failure(LurkError.AuthExpired)
        sessions.reportUnauthorized(platform)
    }

    @Test
    fun rowsFollowSessionState() =
        runTest {
            val vm = viewModel()

            vm.state.test {
                val first = awaitItem()
                assertEquals(listOf(Platform.TWITCH, Platform.KICK), first.rows.map { it.platform })
                assertEquals(AccountStatus.SignedOut, first.status(Platform.TWITCH))
                assertEquals(AccountStatus.SignedOut, first.status(Platform.KICK))

                sessions.signIn(Platform.TWITCH, COOKIE)
                val signedIn = awaitUntil { it.status(Platform.TWITCH) != AccountStatus.SignedOut }
                assertEquals(AccountStatus.SignedIn("Streamer One", null), signedIn.status(Platform.TWITCH))
                assertEquals(AccountStatus.SignedOut, signedIn.status(Platform.KICK))

                sessions.signIn(Platform.KICK, COOKIE)
                expire(Platform.KICK)
                val expired = awaitUntil { it.status(Platform.KICK) is AccountStatus.Expired }
                assertEquals(AccountStatus.Expired("Streamer One"), expired.status(Platform.KICK))

                sessions.signOut(Platform.TWITCH)
                assertEquals(
                    AccountStatus.SignedOut,
                    awaitUntil { it.status(Platform.TWITCH) == AccountStatus.SignedOut }.status(Platform.TWITCH),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun avatarComesFromTheAccount() =
        runTest {
            fetchers.getValue(Platform.KICK).result =
                Outcome.Success(testAccount(Platform.KICK).copy(avatarUrl = "https://example.com/a.png"))
            val vm = viewModel(Platform.KICK)

            assertEquals(
                AccountStatus.SignedIn("Streamer One", "https://example.com/a.png"),
                vm.state.value.status(Platform.KICK),
            )
        }

    @Test
    fun signInOpensLogin() =
        runTest {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(AccountsIntent.SignIn(Platform.KICK))
                assertEquals(AccountsEffect.OpenLogin(Platform.KICK), awaitItem())
            }
        }

    @Test
    fun signOutAsksConfirmFirst() =
        runTest {
            val vm = viewModel(Platform.TWITCH)

            vm.effects.test {
                vm.onIntent(AccountsIntent.SignOut(Platform.TWITCH))
                assertEquals(AccountsEffect.AskConfirmSignOut(Platform.TWITCH), awaitItem())
            }
            runCurrent()
            assertIs<AccountState.SignedIn>(sessions.state(Platform.TWITCH).value)

            vm.onIntent(AccountsIntent.ConfirmSignOut(Platform.TWITCH))
            runCurrent()

            assertEquals(AccountState.SignedOut, sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun confirmSignOutLeavesTheOtherPlatform() =
        runTest {
            val vm = viewModel(Platform.TWITCH, Platform.KICK)

            vm.onIntent(AccountsIntent.ConfirmSignOut(Platform.KICK))
            runCurrent()

            assertEquals(AccountState.SignedOut, sessions.state(Platform.KICK).value)
            assertIs<AccountState.SignedIn>(sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun signOutWhenSignedOutAsksNothing() =
        runTest {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(AccountsIntent.SignOut(Platform.TWITCH))
                expectNoEvents()
            }
        }

    @Test
    fun expiredRowOffersSignInAgain() =
        runTest {
            val vm = viewModel(Platform.TWITCH)
            expire(Platform.TWITCH)

            vm.state.test {
                val expired = awaitUntil { it.status(Platform.TWITCH) is AccountStatus.Expired }
                assertEquals(AccountStatus.Expired("Streamer One"), expired.status(Platform.TWITCH))
            }
            vm.effects.test {
                vm.onIntent(AccountsIntent.SignIn(Platform.TWITCH))
                assertEquals(AccountsEffect.OpenLogin(Platform.TWITCH), awaitItem())
            }
            // Nothing signs out on its own: the expired account stays until the user replaces or removes it.
            assertIs<AccountState.Expired>(sessions.state(Platform.TWITCH).value)
        }

    @Test
    fun expiredAccountCanSignOutAfterConfirm() =
        runTest {
            val vm = viewModel(Platform.KICK)
            expire(Platform.KICK)

            vm.effects.test {
                vm.onIntent(AccountsIntent.SignOut(Platform.KICK))
                assertEquals(AccountsEffect.AskConfirmSignOut(Platform.KICK), awaitItem())
            }
            vm.onIntent(AccountsIntent.ConfirmSignOut(Platform.KICK))
            runCurrent()

            assertEquals(AccountState.SignedOut, sessions.state(Platform.KICK).value)
        }

    @Test
    fun statusHidesTheNameFromToString() {
        assertFalse("Streamer One" in AccountStatus.SignedIn("Streamer One", null).toString())
        assertFalse("Streamer One" in AccountStatus.Expired("Streamer One").toString())
    }

    private companion object {
        const val COOKIE = "test-cookie"
    }
}

package io.github.kemurixd.lurk.feature.common

import app.cash.turbine.test
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.testAccount
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountsStateTest {
    private val twitchFetcher = FakeProfileFetcher(Platform.TWITCH)
    private val kickFetcher = FakeProfileFetcher(Platform.KICK)

    private fun TestScope.sessions() =
        SessionRepository(InMemorySecureStore(), listOf(twitchFetcher, kickFetcher), backgroundScope)

    @Test
    fun accountsStateFollowsSessionRepository() =
        runTest {
            val sessions = sessions()
            val accounts = AccountsState(sessions)

            accounts.states.test {
                assertEquals(Platform.entries.associateWith { AccountState.SignedOut }, awaitItem())

                sessions.signIn(Platform.TWITCH, TOKEN)

                val signedIn = awaitItem()
                assertEquals(AccountState.SignedIn(testAccount(Platform.TWITCH)), signedIn[Platform.TWITCH])
                assertEquals(AccountState.SignedOut, signedIn[Platform.KICK])
            }
            assertEquals(AccountState.SignedIn(testAccount(Platform.TWITCH)), accounts.states.value[Platform.TWITCH])
            assertTrue(accounts.signedIn(Platform.TWITCH))
            assertFalse(accounts.signedIn(Platform.KICK))
        }

    @Test
    fun expiredCountsAsSignedIn() =
        runTest {
            val sessions = sessions()
            val accounts = AccountsState(sessions)
            sessions.signIn(Platform.KICK, TOKEN)
            kickFetcher.result = Outcome.Failure(LurkError.AuthExpired)

            sessions.reportUnauthorized(Platform.KICK)

            assertEquals(AccountState.Expired(testAccount(Platform.KICK)), accounts.states.value[Platform.KICK])
            assertTrue(accounts.signedIn(Platform.KICK))
        }

    @Test
    fun signOutIsSeen() =
        runTest {
            val sessions = sessions()
            val accounts = AccountsState(sessions)
            sessions.signIn(Platform.TWITCH, TOKEN)

            sessions.signOut(Platform.TWITCH)

            assertEquals(AccountState.SignedOut, accounts.states.value[Platform.TWITCH])
            assertFalse(accounts.signedIn(Platform.TWITCH))
        }

    @Test
    fun moduleBindsOneAccountsState() =
        runTest {
            val sessions = sessions()
            val koin =
                koinApplication {
                    modules(module { single { sessions } }, featureCommonModule)
                }.koin

            assertSame(koin.get<AccountsState>(), koin.get<AccountsState>())
        }

    private companion object {
        const val TOKEN = "test-token"
    }
}

package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private class FakeSecureStore : SecureStore {
        val values = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun put(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }

        override suspend fun clear() = values.clear()
    }

    private class FakeFetcher(
        override val platform: Platform,
        var result: Outcome<Account>,
        private val latencyMs: Long = 0,
    ) : ProfileFetcher {
        var calls = 0
        val tokens = mutableListOf<String>()

        override suspend fun profile(token: String): Outcome<Account> {
            calls++
            tokens += token
            if (latencyMs > 0) delay(latencyMs)
            return result
        }
    }

    private val account = Account(Platform.TWITCH, "1000001", "streamer1", "Streamer One", null)
    private val fresh = Account(Platform.TWITCH, "1000001", "streamer1", "Streamer One Renamed", null)
    private val kickAccount = Account(Platform.KICK, "1000002", "streamer2", "Streamer Two", null)
    private val token = "tok-secret-1"

    private fun repo(
        store: SecureStore,
        scope: CoroutineScope,
        vararg fetchers: ProfileFetcher,
    ) = SessionRepository(store, fetchers.toList(), scope)

    private fun FakeSecureStore.seed(
        platform: Platform,
        account: Account?,
        token: String = this@SessionRepositoryTest.token,
    ) {
        values["${platform.id}.token"] = token
        if (account != null) values["${platform.id}.account"] = Json.encodeToString(account)
    }

    /** Restore validates on the scope it was given; `backgroundScope` work only runs when time moves. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    @Test
    fun signInStoresTokenAndAccount() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = repo(store, backgroundScope, fetcher)

            val result = repo.signIn(Platform.TWITCH, token)

            assertEquals(Outcome.Success(account), result)
            assertEquals(AccountState.SignedIn(account), repo.state(Platform.TWITCH).value)
            assertEquals(token, store.values["twitch.token"])
            assertEquals(account, Json.decodeFromString<Account>(store.values.getValue("twitch.account")))
            assertEquals(listOf(token), fetcher.tokens)
        }

    @Test
    fun signInConvertsTheKickCookie() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.KICK, Outcome.Success(kickAccount))
            val repo = repo(store, backgroundScope, fetcher)

            repo.signIn(Platform.KICK, "kat_a%7Cb")

            assertEquals(listOf("kat_a|b"), fetcher.tokens)
            assertEquals("kat_a|b", store.values["kick.token"])
        }

    @Test
    fun signInFailureStoresNothing() =
        runTest {
            val store = FakeSecureStore()
            val repo =
                repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.AuthExpired)))

            val result = repo.signIn(Platform.TWITCH, token)

            assertEquals(Outcome.Failure(LurkError.AuthExpired), result)
            assertTrue(store.values.isEmpty())
            assertEquals(AccountState.SignedOut, repo.state(Platform.TWITCH).value)
        }

    @Test
    fun signInWithoutAFetcherIsUnsupported() =
        runTest {
            val store = FakeSecureStore()
            val repo = repo(store, backgroundScope)

            assertEquals(Outcome.Failure(LurkError.Unsupported), repo.signIn(Platform.KICK, token))
            assertTrue(store.values.isEmpty())
        }

    @Test
    fun restorePublishesTheCachedAccountBeforeValidating() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, account) }
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(fresh), latencyMs = 1_000)
            val repo = repo(store, backgroundScope, fetcher)

            repo.restore()
            assertEquals(AccountState.SignedIn(account), repo.state(Platform.TWITCH).value)

            settle()
            assertEquals(AccountState.SignedIn(fresh), repo.state(Platform.TWITCH).value)
            assertEquals(fresh, Json.decodeFromString<Account>(store.values.getValue("twitch.account")))
        }

    @Test
    fun restoreOfflineKeepsSignedIn() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, account) }
            val repo = repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.Offline)))

            repo.restore()
            settle()

            assertEquals(AccountState.SignedIn(account), repo.state(Platform.TWITCH).value)
            assertEquals(token, repo.token(Platform.TWITCH))
        }

    @Test
    fun restoreTimeoutKeepsSignedIn() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, account) }
            val repo = repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.Timeout)))

            repo.restore()
            settle()

            assertEquals(AccountState.SignedIn(account), repo.state(Platform.TWITCH).value)
        }

    @Test
    fun restoreExpiredMarksExpired() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, account) }
            val repo =
                repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.AuthExpired)))

            repo.restore()
            settle()

            assertEquals(AccountState.Expired(account), repo.state(Platform.TWITCH).value)
            assertNull(repo.token(Platform.TWITCH))
        }

    @Test
    fun restoreSkipsPlatformsWithoutAToken() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, account) }
            val kick = FakeFetcher(Platform.KICK, Outcome.Success(kickAccount))
            val repo = repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Success(account)), kick)

            repo.restore()
            settle()

            assertEquals(0, kick.calls)
            assertEquals(AccountState.SignedOut, repo.state(Platform.KICK).value)
        }

    @Test
    fun restoreWithUnreadableAccountValidatesAndStoresTheFreshOne() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, null) }
            store.values["twitch.account"] = "{not json"
            val repo = repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Success(fresh)))

            repo.restore()
            settle()

            assertEquals(AccountState.SignedIn(fresh), repo.state(Platform.TWITCH).value)
            assertEquals(fresh, Json.decodeFromString<Account>(store.values.getValue("twitch.account")))
        }

    @Test
    fun restoreWithMissingAccountAndExpiredTokenStaysSignedOut() =
        runTest {
            val store = FakeSecureStore().apply { seed(Platform.TWITCH, null) }
            val repo =
                repo(store, backgroundScope, FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.AuthExpired)))

            repo.restore()
            settle()

            assertEquals(AccountState.SignedOut, repo.state(Platform.TWITCH).value)
            assertNull(repo.token(Platform.TWITCH))
        }

    private suspend fun TestScope.signedIn(
        store: FakeSecureStore,
        fetcher: FakeFetcher,
    ): SessionRepository {
        val repo = repo(store, backgroundScope, fetcher)
        repo.signIn(Platform.TWITCH, token)
        fetcher.calls = 0
        return repo
    }

    @Test
    fun concurrentUnauthorizedChecksOnce() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account), latencyMs = 500)
            val repo = signedIn(store, fetcher)

            (1..20).map { async { repo.reportUnauthorized(Platform.TWITCH) } }.awaitAll()

            assertEquals(1, fetcher.calls)
        }

    @Test
    fun laterUnauthorizedChecksAgain() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)

            repo.reportUnauthorized(Platform.TWITCH)
            runCurrent()
            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(2, fetcher.calls)
        }

    @Test
    fun stragglerAfterExpiredCheckDoesNotCheckAgain() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)
            fetcher.result = Outcome.Failure(LurkError.AuthExpired)

            repo.reportUnauthorized(Platform.TWITCH)
            assertIs<AccountState.Expired>(repo.state(Platform.TWITCH).value)
            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(1, fetcher.calls)
        }

    @Test
    fun unauthorizedRecheckSuccessKeepsSignedInWithFreshAccount() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)
            fetcher.result = Outcome.Success(fresh)

            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(AccountState.SignedIn(fresh), repo.state(Platform.TWITCH).value)
            assertEquals(token, repo.token(Platform.TWITCH))
        }

    @Test
    fun unauthorizedRecheckOfflineLeavesStateAlone() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)
            fetcher.result = Outcome.Failure(LurkError.Offline)

            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(AccountState.SignedIn(account), repo.state(Platform.TWITCH).value)
        }

    @Test
    fun unauthorizedWhileSignedOutDoesNothing() =
        runTest {
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = repo(FakeSecureStore(), backgroundScope, fetcher)

            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(0, fetcher.calls)
            assertEquals(AccountState.SignedOut, repo.state(Platform.TWITCH).value)
        }

    @Test
    fun expiredKeepsToken() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)
            fetcher.result = Outcome.Failure(LurkError.AuthExpired)

            repo.reportUnauthorized(Platform.TWITCH)

            assertEquals(AccountState.Expired(account), repo.state(Platform.TWITCH).value)
            assertNull(repo.token(Platform.TWITCH))
            assertEquals(token, store.values["twitch.token"])
        }

    @Test
    fun signOutRemovesEverything() =
        runTest {
            val store = FakeSecureStore()
            val repo = signedIn(store, FakeFetcher(Platform.TWITCH, Outcome.Success(account)))

            repo.signOut(Platform.TWITCH)

            assertTrue(store.values.isEmpty())
            assertEquals(AccountState.SignedOut, repo.state(Platform.TWITCH).value)
            assertNull(repo.token(Platform.TWITCH))
        }

    @Test
    fun tokenNeverInStateToString() =
        runTest {
            val store = FakeSecureStore()
            val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Success(account))
            val repo = signedIn(store, fetcher)
            val signedIn = repo.state(Platform.TWITCH).value.toString()
            fetcher.result = Outcome.Failure(LurkError.AuthExpired)
            repo.reportUnauthorized(Platform.TWITCH)
            val expired = repo.state(Platform.TWITCH).value.toString()

            for (text in listOf(signedIn, expired)) {
                assertFalse(token in text, text)
                assertFalse("streamer1" in text, text)
                assertFalse("Streamer One" in text, text)
                assertFalse("1000001" in text, text)
            }
            assertIs<AccountState.Expired>(repo.state(Platform.TWITCH).value)
        }

    private companion object {
        const val SETTLE_MS = 10_000L
    }
}

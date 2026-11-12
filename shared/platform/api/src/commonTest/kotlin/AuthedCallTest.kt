package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AuthedCallTest {
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
    ) : ProfileFetcher {
        var calls = 0

        override suspend fun profile(token: String): Outcome<Account> {
            calls++
            return result
        }
    }

    private val account = Account(Platform.TWITCH, "1000001", "streamer1", "Streamer One", null)
    private val token = "tok-secret-1"

    private class Fixture(
        val repo: SessionRepository,
        val fetcher: FakeFetcher,
    )

    private suspend fun TestScope.fixture(signedIn: Boolean): Fixture {
        val store = FakeSecureStore()
        if (signedIn) {
            store.values["twitch.token"] = token
            store.values["twitch.account"] = Json.encodeToString(account)
        }
        val fetcher = FakeFetcher(Platform.TWITCH, Outcome.Failure(LurkError.AuthExpired))
        val repo = SessionRepository(store, listOf(fetcher), backgroundScope)
        repo.restore()
        testScheduler.advanceUntilIdle()
        fetcher.calls = 0
        fetcher.result = Outcome.Success(account)
        return Fixture(repo, fetcher)
    }

    @Test
    fun anonymousCallPassesNullToken() =
        runTest {
            val f = fixture(signedIn = false)
            val seen = mutableListOf<String?>()
            val result =
                f.repo.authed(Platform.TWITCH, anonymousFallback = true) { t ->
                    seen += t
                    Outcome.Success(1)
                }
            assertEquals(Outcome.Success(1), result)
            assertEquals(listOf<String?>(null), seen)
            assertEquals(0, f.fetcher.calls)
        }

    @Test
    fun signedInCallPassesToken() =
        runTest {
            val f = fixture(signedIn = true)
            val seen = mutableListOf<String?>()
            val result =
                f.repo.authed(Platform.TWITCH, anonymousFallback = true) { t ->
                    seen += t
                    Outcome.Success(1)
                }
            assertEquals(Outcome.Success(1), result)
            assertEquals(listOf<String?>(token), seen)
        }

    @Test
    fun unauthorizedRetriesAnonymously() =
        runTest {
            val f = fixture(signedIn = true)
            val seen = mutableListOf<String?>()
            val result =
                f.repo.authed(Platform.TWITCH, anonymousFallback = true) { t ->
                    seen += t
                    if (t != null) Outcome.Failure(LurkError.AuthExpired) else Outcome.Success("anon")
                }
            assertEquals(Outcome.Success("anon"), result)
            assertEquals(listOf<String?>(token, null), seen)
            assertEquals(1, f.fetcher.calls)
        }

    @Test
    fun unauthorizedWithoutFallbackFails() =
        runTest {
            val f = fixture(signedIn = true)
            val seen = mutableListOf<String?>()
            val result =
                f.repo.authed<String>(Platform.TWITCH, anonymousFallback = false) { t ->
                    seen += t
                    Outcome.Failure(LurkError.AuthExpired)
                }
            assertEquals(Outcome.Failure(LurkError.AuthExpired), result)
            assertEquals(listOf<String?>(token), seen)
            assertEquals(1, f.fetcher.calls)
        }

    @Test
    fun signedOutUnauthorizedDoesNotReport() =
        runTest {
            val f = fixture(signedIn = false)
            val result =
                f.repo.authed<String>(Platform.TWITCH, anonymousFallback = true) {
                    Outcome.Failure(LurkError.AuthExpired)
                }
            assertEquals(Outcome.Failure(LurkError.AuthExpired), result)
            assertEquals(0, f.fetcher.calls)
        }

    @Test
    fun otherFailureIsReturnedAsIs() =
        runTest {
            val f = fixture(signedIn = true)
            var calls = 0
            val result =
                f.repo.authed<String>(Platform.TWITCH, anonymousFallback = true) {
                    calls++
                    Outcome.Failure(LurkError.Timeout)
                }
            assertEquals(Outcome.Failure(LurkError.Timeout), result)
            assertEquals(1, calls)
            assertEquals(0, f.fetcher.calls)
        }

    @Test
    fun lateUnauthorizedAfterFinishedCheckDoesNotCheckAgain() =
        runTest {
            val f = fixture(signedIn = true)
            runCurrent() // let the restore check finish first
            f.fetcher.calls = 0
            val gate = CompletableDeferred<Unit>()
            val straggler =
                async {
                    f.repo.authed<String>(Platform.TWITCH, anonymousFallback = true) { t ->
                        if (t == null) return@authed Outcome.Success("anon")
                        gate.await()
                        Outcome.Failure(LurkError.AuthExpired)
                    }
                }
            runCurrent()
            val first =
                f.repo.authed<String>(Platform.TWITCH, anonymousFallback = true) { t ->
                    if (t != null) Outcome.Failure(LurkError.AuthExpired) else Outcome.Success("anon")
                }
            assertEquals(1, f.fetcher.calls)

            gate.complete(Unit)

            assertEquals(Outcome.Success("anon"), straggler.await())
            assertEquals(Outcome.Success("anon"), first)
            assertEquals(1, f.fetcher.calls, "the straggler read its token before the finished check")
        }
}

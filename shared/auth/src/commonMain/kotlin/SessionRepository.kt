package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.storage.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one signed-in session per platform. The token and the account (as JSON) live in [SecureStore] under
 * `<platform.id>.token` and `<platform.id>.account` (see [SessionVault]).
 *
 * Profile checks for a platform run one at a time under that platform's lock. Each finished check bumps a generation
 * counter, so callers that were already waiting when a check started reuse its result instead of running another.
 */
class SessionRepository(
    secure: SecureStore,
    fetchers: List<ProfileFetcher>,
    private val scope: CoroutineScope,
) {
    private class Session {
        val state = MutableStateFlow<AccountState>(AccountState.SignedOut)
        val lock = Mutex()
        val generation = MutableStateFlow(0L)
    }

    private val vault = SessionVault(secure)
    private val fetchers = fetchers.associateBy { it.platform }
    private val sessions = Platform.entries.associateWith { Session() }

    fun state(platform: Platform): StateFlow<AccountState> = session(platform).state.asStateFlow()

    /**
     * Publishes the cached account of every platform that has a stored token, then validates each token on the app
     * scope. A rejected token gives [AccountState.Expired]; an offline or slow check keeps the cached state.
     */
    suspend fun restore() {
        for (platform in Platform.entries) {
            if (vault.token(platform) == null) continue
            val session = session(platform)
            vault.account(platform)?.let { session.state.value = AccountState.SignedIn(it) }
            val seen = session.generation.value
            scope.launch { checkOnce(platform, seen) }
        }
    }

    /** Signs in with the session cookie from the login page. Nothing is stored unless the profile check succeeds. */
    suspend fun signIn(
        platform: Platform,
        cookieValue: String,
    ): Outcome<Account> {
        val fetcher = fetchers[platform] ?: return Outcome.Failure(LurkError.Unsupported)
        val token = LoginSpec.forPlatform(platform).tokenFrom(cookieValue)
        val session = session(platform)
        return session.lock.withLock {
            val result = fetcher.profile(token)
            if (result is Outcome.Success) {
                vault.putToken(platform, token)
                vault.putAccount(platform, result.value)
                session.state.value = AccountState.SignedIn(result.value)
            }
            result
        }
    }

    suspend fun signOut(platform: Platform) {
        val session = session(platform)
        session.lock.withLock {
            vault.remove(platform)
            session.state.value = AccountState.SignedOut
        }
    }

    /** The session token, or null unless the platform is [AccountState.SignedIn]. */
    suspend fun token(platform: Platform): String? =
        if (session(platform).state.value is AccountState.SignedIn) vault.token(platform) else null

    /**
     * Called when a request came back 401. Re-checks the token once; callers that arrive while a check is pending wait
     * for it and share its result. Only a rejected token moves the state to [AccountState.Expired].
     */
    suspend fun reportUnauthorized(platform: Platform) {
        val session = session(platform)
        if (session.state.value == AccountState.SignedOut) return
        checkOnce(platform, session.generation.value)
    }

    private suspend fun checkOnce(
        platform: Platform,
        seen: Long,
    ) {
        val session = session(platform)
        session.lock.withLock {
            if (session.generation.value != seen) return
            try {
                validate(platform, session)
            } finally {
                session.generation.value = seen + 1
            }
        }
    }

    private suspend fun validate(
        platform: Platform,
        session: Session,
    ) {
        val fetcher = fetchers[platform] ?: return
        val token = vault.token(platform) ?: return
        when (val result = fetcher.profile(token)) {
            is Outcome.Success -> {
                vault.putAccount(platform, result.value)
                session.state.value = AccountState.SignedIn(result.value)
            }

            is Outcome.Failure -> {
                if (result.error == LurkError.AuthExpired) markExpired(session)
            }
        }
    }

    private fun markExpired(session: Session) {
        val account =
            when (val current = session.state.value) {
                is AccountState.SignedIn -> current.account
                is AccountState.Expired -> current.account
                AccountState.SignedOut -> return
            }
        session.state.value = AccountState.Expired(account)
    }

    private fun session(platform: Platform): Session = sessions.getValue(platform)
}

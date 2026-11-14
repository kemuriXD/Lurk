package io.github.kemurixd.lurk.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.map
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val log = Logger.withTag("SignIn")

/**
 * Signs in to [platform] with the session cookie the login WebView picked up.
 *
 * The first [LoginIntent.CookieFound] starts one `signIn`; more of them while it runs are ignored. Success emits
 * [LoginEffect.ClearCookies] then [LoginEffect.Done]. Failure moves to [LoginUiState.Failed] and emits
 * [LoginEffect.ClearCookies], and the login stays open: the next cookie found tries again. [LoginIntent.Cancel] emits
 * the same pair as success and stops caring about a running sign-in: that call runs to the end, so the session is
 * stored whole or not at all, and its result is dropped. After [LoginEffect.Done] every intent is ignored.
 *
 * The state has no upstream to share, so it is a plain [StateFlow] rather than `stateIn`. The cookie value is held
 * only for the call and never logged. Call [onIntent] from the main thread.
 */
class LoginViewModel(
    private val platform: Platform,
    private val sessions: SessionRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)

    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<LoginEffect>(Channel.BUFFERED)

    /** One-shot effects. Never replayed: a collector that returns sees only what is sent after it. */
    val effects: Flow<LoginEffect> = effectChannel.receiveAsFlow()

    // The running sign-in. A job that is no longer the one stored here was given up on and drops its result.
    private var signInJob: Job? = null
    private var finished = false

    fun onIntent(intent: LoginIntent) {
        if (finished) return
        when (intent) {
            LoginIntent.PageLoaded -> {
                if (mutableState.value == LoginUiState.Loading) mutableState.value = LoginUiState.Ready
            }

            is LoginIntent.CookieFound -> {
                if (mutableState.value != LoginUiState.SigningIn) signIn(intent.value)
            }

            LoginIntent.Cancel -> {
                // Not cancelled: the sign-in finishes its session write, and job identity drops its result.
                signInJob = null
                finish()
            }
        }
    }

    private fun signIn(cookieValue: String) {
        mutableState.value = LoginUiState.SigningIn
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val me = currentCoroutineContext().job
                val result = withContext(dispatchers.io) { guardedSignIn(cookieValue) }
                if (signInJob !== me) return@launch
                signInJob = null
                when (result) {
                    is Outcome.Success -> {
                        finish()
                    }

                    is Outcome.Failure -> {
                        mutableState.value = LoginUiState.Failed(result.error)
                        effectChannel.trySend(LoginEffect.ClearCookies)
                    }
                }
            }
        signInJob = job
        job.start()
    }

    /** Any exception but cancellation becomes a failure. Only its class is logged: a message could carry the cookie. */
    private suspend fun guardedSignIn(cookieValue: String): Outcome<Unit> =
        try {
            sessions.signIn(platform, cookieValue).map { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LurkException) {
            Outcome.Failure(e.error)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w { "sign-in failed unexpectedly: ${e::class.simpleName}" }
            Outcome.Failure(LurkError.Unexpected)
        }

    private fun finish() {
        finished = true
        effectChannel.trySend(LoginEffect.ClearCookies)
        effectChannel.trySend(LoginEffect.Done)
    }
}

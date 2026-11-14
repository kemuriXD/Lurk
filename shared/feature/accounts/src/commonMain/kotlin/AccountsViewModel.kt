package io.github.kemurixd.lurk.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.AccountsState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Accounts section of Settings: each platform's sign-in state, read from [AccountsState].
 *
 * Signing in only asks the screen to open the login ([AccountsEffect.OpenLogin]); the login screen does the rest.
 * Signing out asks first ([AccountsEffect.AskConfirmSignOut]) and acts on [AccountsIntent.ConfirmSignOut].
 *
 * [effects] hands each effect to one collector, once. Call [onIntent] from the main thread.
 */
class AccountsViewModel(
    private val accounts: AccountsState,
    private val sessions: SessionRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val effectChannel = Channel<AccountsEffect>(Channel.BUFFERED)

    /** One-shot effects. Never replayed: a collector that returns sees only what is sent after it. */
    val effects: Flow<AccountsEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<AccountsUiState> =
        accounts.states.map(::uiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = uiState(accounts.states.value),
        )

    fun onIntent(intent: AccountsIntent) {
        when (intent) {
            is AccountsIntent.SignIn -> {
                effectChannel.trySend(AccountsEffect.OpenLogin(intent.platform))
            }

            is AccountsIntent.SignOut -> {
                if (accounts.signedIn(intent.platform)) {
                    effectChannel.trySend(AccountsEffect.AskConfirmSignOut(intent.platform))
                }
            }

            is AccountsIntent.ConfirmSignOut -> {
                signOut(intent.platform)
            }
        }
    }

    private fun signOut(platform: Platform) {
        viewModelScope.launch { withContext(dispatchers.io) { sessions.signOut(platform) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun uiState(states: Map<Platform, AccountState>): AccountsUiState =
            AccountsUiState(Platform.entries.map { AccountRow(it, states[it].toStatus()) })

        fun AccountState?.toStatus(): AccountStatus =
            when (this) {
                null, AccountState.SignedOut -> AccountStatus.SignedOut
                is AccountState.SignedIn -> AccountStatus.SignedIn(account.displayName, account.avatarUrl)
                is AccountState.Expired -> AccountStatus.Expired(account.displayName)
            }
    }
}

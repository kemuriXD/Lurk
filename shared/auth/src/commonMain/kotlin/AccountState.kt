package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.model.Account

/** Sign-in state of one platform. [Account.toString] hides the identity, so these are safe to log. */
sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(
        val account: Account,
    ) : AccountState

    /** The platform rejected the stored token. The token is kept so the user can sign in again over it. */
    data class Expired(
        val account: Account,
    ) : AccountState
}

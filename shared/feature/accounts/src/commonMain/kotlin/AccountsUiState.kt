package io.github.kemurixd.lurk.feature.accounts

import io.github.kemurixd.lurk.core.model.Platform

/** The Accounts section: one row per platform, Twitch first. */
data class AccountsUiState(
    val rows: List<AccountRow>,
)

data class AccountRow(
    val platform: Platform,
    val status: AccountStatus,
)

/**
 * What a row shows. Only display data crosses to the UI; tokens never leave the session store. [toString] hides the
 * name, as `Account` does, so a logged state can't identify the user.
 */
sealed interface AccountStatus {
    data object SignedOut : AccountStatus

    data class SignedIn(
        val displayName: String,
        val avatarUrl: String?,
    ) : AccountStatus {
        override fun toString(): String = "SignedIn"
    }

    /** The platform rejected the stored token. The row offers "Sign in again"; nothing signs out on its own. */
    data class Expired(
        val displayName: String,
    ) : AccountStatus {
        override fun toString(): String = "Expired"
    }
}

/** What the Accounts section can ask of [AccountsViewModel]. */
sealed interface AccountsIntent {
    /** Sign in, or sign in again over an expired session. Opens the login. */
    data class SignIn(
        val platform: Platform,
    ) : AccountsIntent

    /** The Sign out button. Asks for confirmation first; nothing changes yet. */
    data class SignOut(
        val platform: Platform,
    ) : AccountsIntent

    /** The user confirmed the dialog: removes the stored session. */
    data class ConfirmSignOut(
        val platform: Platform,
    ) : AccountsIntent
}

/** One-shot results for the screen to act on. Each is delivered to one collector, once. */
sealed interface AccountsEffect {
    data class OpenLogin(
        val platform: Platform,
    ) : AccountsEffect

    data class AskConfirmSignOut(
        val platform: Platform,
    ) : AccountsEffect
}

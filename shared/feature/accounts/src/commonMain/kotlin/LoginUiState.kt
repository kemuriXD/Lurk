package io.github.kemurixd.lurk.feature.accounts

import io.github.kemurixd.lurk.core.common.LurkError

/** The login page around the platform's WebView. */
sealed interface LoginUiState {
    /** The cookies are being cleared and the login page hasn't finished loading yet. */
    data object Loading : LoginUiState

    /** The login page is up; the user is signing in on it. */
    data object Ready : LoginUiState

    /** The session cookie was found and is being checked. */
    data object SigningIn : LoginUiState

    /** The check failed. The page stays open, with cookies cleared, for another try. */
    data class Failed(
        val error: LurkError,
    ) : LoginUiState
}

/** What the login screen can tell [LoginViewModel]. */
sealed interface LoginIntent {
    /** A page finished loading in the WebView. The first one makes the login [LoginUiState.Ready]. */
    data object PageLoaded : LoginIntent

    /** The session cookie appeared. [toString] hides [value], so a logged intent never carries it. */
    data class CookieFound(
        val value: String,
    ) : LoginIntent {
        override fun toString(): String = "CookieFound"
    }

    /** Cancel or back: leave without signing in. */
    data object Cancel : LoginIntent
}

/** One-shot results for the login screen. Each is delivered to one collector, once. */
sealed interface LoginEffect {
    /** Clear the WebView's cookies and storage, so no session cookie stays behind. */
    data object ClearCookies : LoginEffect

    /** Close the login. Always comes after [ClearCookies]. */
    data object Done : LoginEffect
}

package io.github.kemurixd.lurk.designsystem

import androidx.annotation.StringRes
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.isRetryable
import io.github.kemurixd.lurk.core.model.Platform
import kotlin.time.Duration

/** The GitHub Releases page, where "Check for update" goes. */
const val RELEASES_URL = "https://github.com/kemuriXD/Lurk/releases"

/** The one thing an error surface offers the user to do about a failure. Every surface maps it the same way. */
sealed interface ErrorAction {
    /** Ask again. With [after], the button counts down and stays off until the wait is over. */
    data class Retry(
        val after: Duration? = null,
    ) : ErrorAction

    /** Open the failed platform's login. [again] when a stored session lapsed. */
    data class SignIn(
        val again: Boolean,
    ) : ErrorAction

    data object OpenProxySettings : ErrorAction

    /** Open the GitHub Releases page ([RELEASES_URL]). */
    data object CheckForUpdate : ErrorAction

    data object GoBack : ErrorAction
}

/**
 * What to offer for this failure (the §6 table). Retry only when asking again can help ([isRetryable]), except on a
 * first-page load ([firstPage]), which always offers Retry where the table offers nothing else (ruling R41).
 */
fun LurkError.action(firstPage: Boolean = false): ErrorAction? {
    val own =
        when (this) {
            LurkError.ProxyFailed -> ErrorAction.OpenProxySettings
            LurkError.SchemaChanged -> ErrorAction.CheckForUpdate
            LurkError.NotFound -> ErrorAction.GoBack
            LurkError.NotSignedIn -> ErrorAction.SignIn(again = false)
            LurkError.AuthExpired -> ErrorAction.SignIn(again = true)
            is LurkError.RateLimited -> ErrorAction.Retry(after = retryAfter)
            else -> if (isRetryable) ErrorAction.Retry() else null
        }
    return own ?: if (firstPage) ErrorAction.Retry() else null
}

/** What a snackbar offers for this failure: the table's action, except Go back, which only a full screen offers. */
fun LurkError.snackbarAction(): ErrorAction? = action().takeUnless { it == ErrorAction.GoBack }

/** The action as offered where [platform] is the failed one: Sign in needs a platform, so without one it is dropped. */
internal fun ErrorAction?.forPlatform(platform: Platform?): ErrorAction? =
    if (this is ErrorAction.SignIn && platform == null) null else this

/** The button's plain verb. */
@StringRes
fun ErrorAction.labelRes(): Int =
    when (this) {
        is ErrorAction.Retry -> R.string.error_action_retry
        is ErrorAction.SignIn -> if (again) R.string.error_action_sign_in_again else R.string.error_action_sign_in
        ErrorAction.OpenProxySettings -> R.string.error_action_open_proxy_settings
        ErrorAction.CheckForUpdate -> R.string.error_action_check_for_update
        ErrorAction.GoBack -> R.string.error_action_go_back
    }

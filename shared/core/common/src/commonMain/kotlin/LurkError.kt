package io.github.kemurixd.lurk.core.common

import kotlin.time.Duration

/** Every failure the app distinguishes. Transport and parsing code maps into these. */
sealed interface LurkError {
    data object Offline : LurkError

    data object Timeout : LurkError

    data object AuthExpired : LurkError

    data object Blocked : LurkError

    data object IntegrityRequired : LurkError

    data object ProxyFailed : LurkError

    data object SchemaChanged : LurkError

    data object NotFound : LurkError

    data object NotSignedIn : LurkError

    data object Unsupported : LurkError

    /** A failure no layer expected; logged where it was caught so it stays visible. */
    data object Unexpected : LurkError

    data class Http(
        val code: Int,
    ) : LurkError

    data class RateLimited(
        val retryAfter: Duration?,
    ) : LurkError
}

/**
 * Whether asking again can plausibly succeed. False for failures that repeat until something outside the call changes:
 * a platform check the app can't pass, a changed schema, a block, a lapsed session, a missing item or a client error.
 */
val LurkError.isRetryable: Boolean
    get() =
        when (this) {
            LurkError.Offline, LurkError.Timeout, LurkError.ProxyFailed, LurkError.Unexpected -> true

            is LurkError.RateLimited -> true

            is LurkError.Http -> code >= 500

            LurkError.AuthExpired,
            LurkError.Blocked,
            LurkError.IntegrityRequired,
            LurkError.SchemaChanged,
            LurkError.NotFound,
            LurkError.NotSignedIn,
            LurkError.Unsupported,
            -> false
        }

/** Carries a [LurkError] across layers that throw; [outcomeOf] turns it back into a value. */
class LurkException(
    val error: LurkError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

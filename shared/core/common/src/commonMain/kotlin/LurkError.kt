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

    /** A failure no layer expected; logged where it was caught so it stays visible. */
    data object Unexpected : LurkError

    data class Http(
        val code: Int,
    ) : LurkError

    data class RateLimited(
        val retryAfter: Duration?,
    ) : LurkError
}

/** Carries a [LurkError] across layers that throw; [outcomeOf] turns it back into a value. */
class LurkException(
    val error: LurkError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

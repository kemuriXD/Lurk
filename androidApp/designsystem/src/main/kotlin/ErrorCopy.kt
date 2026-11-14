package io.github.kemurixd.lurk.designsystem

import androidx.annotation.StringRes
import io.github.kemurixd.lurk.core.common.LurkError

/** The user-facing sentence for an error. The `when` is exhaustive, so a new subtype breaks the build. */
@StringRes
fun LurkError.messageRes(): Int =
    when (this) {
        LurkError.Offline -> R.string.error_offline
        LurkError.Timeout -> R.string.error_timeout
        LurkError.AuthExpired -> R.string.error_auth_expired
        LurkError.Blocked -> R.string.error_blocked
        LurkError.IntegrityRequired -> R.string.error_integrity_required
        LurkError.ProxyFailed -> R.string.error_proxy_failed
        LurkError.SchemaChanged -> R.string.error_schema_changed
        LurkError.NotFound -> R.string.error_not_found
        LurkError.NotSignedIn -> R.string.error_not_signed_in
        LurkError.Unsupported -> R.string.error_unsupported
        LurkError.Unexpected -> R.string.error_unexpected
        is LurkError.Http -> R.string.error_http
        is LurkError.RateLimited -> R.string.error_rate_limited
    }

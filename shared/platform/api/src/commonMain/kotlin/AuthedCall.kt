package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform

/**
 * Runs [call] with the current session token (null when signed out or expired). When the call comes back with
 * `AuthExpired` and a token was used, the session is re-checked once through [SessionRepository.reportUnauthorized],
 * unless a check already finished after the token was read.
 * Then the call runs again without a token if [anonymousFallback] is set, or `AuthExpired` is returned as is.
 * Any other result is returned untouched.
 */
suspend fun <T> SessionRepository.authed(
    platform: Platform,
    anonymousFallback: Boolean,
    call: suspend (token: String?) -> Outcome<T>,
): Outcome<T> {
    val checksSeen = checksFinished(platform)
    val token = token(platform)
    val result = call(token)
    val expired = result is Outcome.Failure && result.error == LurkError.AuthExpired
    if (token == null || !expired) return result
    reportUnauthorized(platform, token, checksSeen)
    return if (anonymousFallback) call(null) else result
}

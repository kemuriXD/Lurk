package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.authed

/**
 * Runs [call] with the Twitch token. An expired token falls back to a signed-out call, and so does an integrity
 * failure on a signed-in call.
 */
internal suspend fun <T> SessionRepository.twitchCall(call: suspend (token: String?) -> Outcome<T>): Outcome<T> =
    authed(Platform.TWITCH, anonymousFallback = true) { token -> retryAnonymouslyOnIntegrity(token, call) }

/** [call] with [token]; when that fails the integrity check and [token] is set, once more with none. */
internal suspend fun <T> retryAnonymouslyOnIntegrity(
    token: String?,
    call: suspend (token: String?) -> Outcome<T>,
): Outcome<T> {
    val result = call(token)
    val integrity = result is Outcome.Failure && result.error == LurkError.IntegrityRequired
    return if (token != null && integrity) call(null) else result
}

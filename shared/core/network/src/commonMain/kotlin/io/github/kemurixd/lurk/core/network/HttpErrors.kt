package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import kotlinx.io.IOException
import kotlin.time.Instant

private const val FIRST_ERROR_STATUS = 400
private val challengeMarkers = listOf("challenge-platform", "Just a moment")

/** Maps an HTTP response to a [LurkError], or null when the status is below 400. */
fun mapHttpError(
    status: Int,
    headers: Headers,
    bodySnippet: String,
    now: Instant,
): LurkError? {
    if (status < FIRST_ERROR_STATUS) return null
    return when (status) {
        Unauthorized.value -> LurkError.AuthExpired
        Forbidden.value -> if (isChallenge(headers, bodySnippet)) LurkError.Blocked else LurkError.Http(status)
        NotFound.value -> LurkError.NotFound
        TooManyRequests.value -> LurkError.RateLimited(parseRetryAfter(headers[HttpHeaders.RetryAfter], now))
        else -> LurkError.Http(status)
    }
}

/** Maps a transport failure to a [LurkError], or null when it is not a network problem. */
fun mapThrowable(t: Throwable): LurkError? =
    when (t) {
        is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> LurkError.Timeout
        is IOException -> LurkError.Offline
        else -> null
    }

private fun isChallenge(
    headers: Headers,
    body: String,
): Boolean = headers.contains("cf-mitigated") || challengeMarkers.any { body.contains(it) }

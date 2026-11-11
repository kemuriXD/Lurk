package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.Headers
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HttpErrorsTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun cfMitigatedHeaderIsBlocked() {
        assertEquals(LurkError.Blocked, mapHttpError(403, headersOf("cf-mitigated", "challenge"), "", now))
    }

    @Test
    fun challengePageBodyIsBlocked() {
        assertEquals(LurkError.Blocked, mapHttpError(403, Headers.Empty, "<title>Just a moment...</title>", now))
        assertEquals(LurkError.Blocked, mapHttpError(403, Headers.Empty, "/cdn-cgi/challenge-platform/h/b", now))
    }

    @Test
    fun plainForbiddenIsHttp403() {
        assertEquals(LurkError.Http(403), mapHttpError(403, Headers.Empty, "{}", now))
    }

    @Test
    fun statusCodesMapToErrors() {
        assertEquals(LurkError.AuthExpired, mapHttpError(401, Headers.Empty, "", now))
        assertEquals(LurkError.NotFound, mapHttpError(404, Headers.Empty, "", now))
        assertEquals(LurkError.Http(500), mapHttpError(500, Headers.Empty, "", now))
    }

    @Test
    fun tooManyRequestsCarriesRetryAfter() {
        assertEquals(
            LurkError.RateLimited(5.seconds),
            mapHttpError(429, headersOf("Retry-After", "5"), "", now),
        )
        assertEquals(LurkError.RateLimited(null), mapHttpError(429, Headers.Empty, "", now))
    }

    @Test
    fun successIsNotAnError() {
        assertNull(mapHttpError(200, Headers.Empty, "", now))
        assertNull(mapHttpError(399, Headers.Empty, "", now))
    }

    @Test
    fun throwablesMapToTransportErrors() {
        assertEquals(LurkError.Timeout, mapThrowable(SocketTimeoutException("read timed out")))
        assertEquals(LurkError.Offline, mapThrowable(IOException("connection reset")))
        assertNull(mapThrowable(IllegalStateException("bug")))
    }
}

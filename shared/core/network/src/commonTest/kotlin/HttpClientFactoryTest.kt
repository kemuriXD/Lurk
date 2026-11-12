package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class HttpClientFactoryTest {
    @Serializable
    private data class Channel(
        val slug: String,
    )

    @Test
    fun tooManyRequestsThrowsRateLimited() =
        runTest {
            val engine =
                MockEngine { respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "5")) }
            val client = createHttpClient(engine)
            val e = assertFailsWith<LurkException> { client.get("https://example.test/api") }
            assertEquals(LurkError.RateLimited(5.seconds), e.error)
        }

    @Test
    fun challengePageThrowsBlocked() =
        runTest {
            val engine = MockEngine { respond("<title>Just a moment...</title>", HttpStatusCode.Forbidden) }
            val client = createHttpClient(engine)
            val e = assertFailsWith<LurkException> { client.get("https://example.test/api") }
            assertEquals(LurkError.Blocked, e.error)
        }

    @Test
    fun jsonWithUnknownFieldsDecodes() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"slug":"somebody","extra":1,"nested":{"a":null}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createHttpClient(engine)
            assertEquals(Channel("somebody"), client.get("https://example.test/api").body<Channel>())
        }
}

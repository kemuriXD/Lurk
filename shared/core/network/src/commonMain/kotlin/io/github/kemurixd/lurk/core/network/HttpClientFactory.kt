package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val BODY_SNIPPET_CHARS = 2_048
private const val HTTP_ERROR_FLOOR = 400

/** The platform's default engine: OkHttp on Android and the JVM. */
expect fun defaultEngine(): HttpClientEngine

/** Builds the shared client. Error responses and transport failures surface as [LurkException]. */
fun createHttpClient(
    engine: HttpClientEngine = defaultEngine(),
    cookies: CookiesStorage = AcceptAllCookiesStorage(),
): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(WebSockets)
        install(HttpCookies) {
            storage = cookies
        }
        HttpResponseValidator {
            validateResponse { response ->
                val status = response.status.value
                val snippet = if (status >= HTTP_ERROR_FLOOR) response.bodyAsText().take(BODY_SNIPPET_CHARS) else ""
                val error = mapHttpError(status, response.headers, snippet, Clock.System.now())
                if (error != null) throw LurkException(error)
            }
            handleResponseExceptionWithRequest { cause, _ ->
                val error = mapThrowable(cause)
                if (error != null) throw LurkException(error, cause)
            }
        }
    }

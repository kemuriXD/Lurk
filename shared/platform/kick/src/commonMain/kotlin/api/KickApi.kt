package io.github.kemurixd.lurk.platform.kick.api

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.BrowserProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Which of Kick's web API roots a request goes to. */
enum class KickBase { API, WEB, SEARCH }

/**
 * Sends requests to Kick's web API with browser headers, and an optional bearer token for signed-in calls.
 * Cloudflare blocks go through the [gate].
 */
class KickApi(
    private val http: HttpClient,
    private val config: StateFlow<RemoteConfig>,
    private val gate: CloudflareGate,
) {
    suspend fun get(
        base: KickBase,
        path: String,
        token: String?,
        query: Map<String, String> = emptyMap(),
    ): Outcome<JsonElement> =
        when (val reply = execute(HttpMethod.Get, urlOf(base, path, query), token, body = null)) {
            is Outcome.Success -> reply.value?.let { Outcome.Success(it) } ?: Outcome.Failure(LurkError.SchemaChanged)
            is Outcome.Failure -> reply
        }

    suspend fun send(
        method: HttpMethod,
        base: KickBase,
        path: String,
        token: String?,
        body: JsonElement?,
    ): Outcome<JsonElement?> = execute(method, urlOf(base, path, emptyMap()), token, body)

    private suspend fun execute(
        method: HttpMethod,
        url: String,
        token: String?,
        body: JsonElement?,
    ): Outcome<JsonElement?> = gate.guard(url) { outcomeOf { request(method, url, token, body) } }

    private suspend fun request(
        method: HttpMethod,
        url: String,
        token: String?,
        body: JsonElement?,
    ): JsonElement? {
        val text =
            http
                .request {
                    this.method = method
                    url(url)
                    BrowserProfile.headers.forEach { (name, value) -> header(name, value) }
                    headers.remove(HttpHeaders.Accept)
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    header("x-app-platform", "web")
                    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                    if (body != null) setBody(TextContent(body.toString(), ContentType.Application.Json))
                }.bodyAsText()
        return if (text.isBlank()) null else Json.parseToJsonElement(text)
    }

    private fun urlOf(
        base: KickBase,
        path: String,
        query: Map<String, String>,
    ): String {
        val kick = config.value.kick
        val root =
            when (base) {
                KickBase.API -> kick.apiBase
                KickBase.WEB -> kick.webApiBase
                KickBase.SEARCH -> kick.searchApiBase
            }
        return URLBuilder(root.trimEnd('/') + "/" + path.trimStart('/'))
            .apply { query.forEach { (key, value) -> parameters.append(key, value) } }
            .buildString()
    }

    override fun toString(): String = "KickApi"
}

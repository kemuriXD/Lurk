package io.github.kemurixd.lurk.platform.twitch.gql

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.ChallengeKind
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CHALLENGE_URL = "https://www.twitch.tv/"
private const val INTEGRITY_COOKIE = "client-integrity"

/**
 * Posts raw GraphQL operations to Twitch's web endpoint and returns the `data` object, decoded by the caller.
 *
 * A failed integrity check on a signed-in request asks the [solver] once and replays the request with the token
 * it returns. The anonymous fallback is the caller's decision.
 */
class GqlClient(
    private val http: HttpClient,
    private val config: StateFlow<RemoteConfig>,
    private val deviceId: DeviceId,
    private val dispatchers: DispatcherProvider,
    private val solver: ChallengeSolver?,
) {
    suspend fun <T> query(
        operation: String,
        query: String,
        variables: JsonObject,
        token: String?,
        decode: (JsonObject) -> T,
    ): Outcome<T> =
        outcomeOf {
            val body = requestBody(operation, query, variables)
            var reply = send(body, token, integrity = null)
            if (reply is GqlReply.IntegrityFailed) {
                val integrity = solveIntegrity(token) ?: throw LurkException(LurkError.IntegrityRequired)
                reply = send(body, token, integrity)
            }
            val data = dataOf(reply)
            withContext(dispatchers.default) { decodeOrSchemaChanged(data, decode) }
        }

    private suspend fun send(
        body: String,
        token: String?,
        integrity: String?,
    ): GqlReply {
        val twitch = config.value.twitch
        val id = deviceId.get()
        val text =
            http
                .post(twitch.gqlUrl) {
                    header("Client-Id", twitch.gqlClientId)
                    header("X-Device-Id", id)
                    if (token != null) header(HttpHeaders.Authorization, "OAuth $token")
                    if (integrity != null) header("Client-Integrity", integrity)
                    setBody(TextContent(body, ContentType.Application.Json))
                }.bodyAsText()
        return withContext(dispatchers.default) { classifyReply(Json.parseToJsonElement(text)) }
    }

    /** The integrity token for a signed-in request, or null when there is no one to ask or nothing came back. */
    private suspend fun solveIntegrity(token: String?): String? {
        if (token == null) return null
        val result = solver?.solve(ChallengeKind.TWITCH_INTEGRITY, CHALLENGE_URL)
        return (result as? ChallengeResult.Solved)?.cookies?.get(INTEGRITY_COOKIE)?.takeIf { it.isNotBlank() }
    }

    private fun dataOf(reply: GqlReply): JsonObject =
        when (reply) {
            is GqlReply.Data -> reply.data
            GqlReply.AuthExpired -> throw LurkException(LurkError.AuthExpired)
            GqlReply.IntegrityFailed -> throw LurkException(LurkError.IntegrityRequired)
            GqlReply.UnexpectedShape -> throw LurkException(LurkError.SchemaChanged)
        }

    private fun requestBody(
        operation: String,
        query: String,
        variables: JsonObject,
    ): String =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("operationName", operation)
                    put("query", query)
                    put("variables", variables)
                },
            )
        }.toString()
}

@Suppress("TooGenericExceptionCaught")
private fun <T> decodeOrSchemaChanged(
    data: JsonObject,
    decode: (JsonObject) -> T,
): T =
    try {
        decode(data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: LurkException) {
        throw e
    } catch (e: Exception) {
        throw LurkException(LurkError.SchemaChanged, e)
    }

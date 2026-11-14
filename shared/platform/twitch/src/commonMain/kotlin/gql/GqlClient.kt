package io.github.kemurixd.lurk.platform.twitch.gql

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.network.AlwaysOnline
import io.github.kemurixd.lurk.core.network.ChallengeKind
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.core.network.NetworkStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val CHALLENGE_URL = "https://www.twitch.tv/"
private const val INTEGRITY_COOKIE = "client-integrity"
private const val SERVER_ERROR_FLOOR = 500
private const val MAX_RETRIES = 3

/** The least time between two load-more requests of one browse list. */
private val LOAD_MORE_INTERVAL = 500.milliseconds
private val BACKOFF_BASE = 1.seconds

/**
 * Posts raw GraphQL operations to Twitch's web endpoint and returns the `data` object, decoded by the caller.
 *
 * A failed integrity check on a signed-in request asks the [solver] once and replays the request with the token
 * it returns. The anonymous fallback is the caller's decision.
 *
 * The browse lists use [discoveryQuery] instead: anonymous, under the discovery client IDs, which page without an
 * integrity token, and with their own [discoveryDeviceId], so they can't be linked to the signed-in [deviceId].
 */
class GqlClient(
    private val http: HttpClient,
    private val config: StateFlow<RemoteConfig>,
    private val deviceId: DeviceId,
    private val dispatchers: DispatcherProvider,
    private val solver: ChallengeSolver?,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val network: NetworkStatus = AlwaysOnline,
    private val discoveryDeviceId: DeviceId,
) {
    private val workingDiscoveryId = MutableStateFlow<String?>(null)
    private val pacing = Mutex()
    private val lastRequest = mutableMapOf<String, TimeMark>()

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
            withContext(dispatchers.default) { decodeReply(reply, decode) }
        }

    /**
     * Runs a browse-list operation without the user's token, under the first discovery client ID that Twitch's
     * integrity check lets through. An ID that fails it hands over to the next, and the one that works is used first
     * from then on. Load-more requests (those with an `after`) wait [LOAD_MORE_INTERVAL] after the previous one of
     * the same operation, and a rate limit or server error is retried with a 1, 2 and 4 second backoff.
     */
    suspend fun <T> discoveryQuery(
        operation: String,
        query: String,
        variables: JsonObject,
        decode: (JsonObject) -> T,
    ): Outcome<T> =
        outcomeOf {
            val body = requestBody(operation, query, variables)
            if ("after" in variables) awaitLoadMoreSlot(operation)
            markRequest(operation)
            val twitch = config.value.twitch
            val ids = twitch.gqlDiscoveryClientIds.ifEmpty { listOf(twitch.gqlClientId) }
            val start = ids.indexOf(workingDiscoveryId.value).coerceAtLeast(0)
            var reply: GqlReply = GqlReply.IntegrityFailed
            for (offset in ids.indices) {
                val id = ids[(start + offset) % ids.size]
                reply = sendWithBackoff(body, id)
                if (reply !is GqlReply.IntegrityFailed) {
                    workingDiscoveryId.value = id
                    break
                }
            }
            withContext(dispatchers.default) { decodeReply(reply, decode) }
        }

    private suspend fun awaitLoadMoreSlot(operation: String) {
        val last = pacing.withLock { lastRequest[operation] } ?: return
        val wait = LOAD_MORE_INTERVAL - last.elapsedNow()
        if (wait.isPositive()) delay(wait)
    }

    private suspend fun markRequest(operation: String) {
        pacing.withLock { lastRequest[operation] = timeSource.markNow() }
    }

    private suspend fun sendWithBackoff(
        body: String,
        clientId: String,
    ): GqlReply {
        var attempt = 0
        while (true) {
            try {
                return send(body, token = null, integrity = null, clientId = clientId, device = discoveryDeviceId)
            } catch (e: LurkException) {
                val wait = retryDelay(e.error, attempt) ?: throw e
                // A retry waits for a server that may answer; with the network gone nobody will.
                if (!network.isOnline.value) throw LurkException(LurkError.Offline, e)
                delay(wait)
                attempt++
            }
        }
    }

    private fun retryDelay(
        error: LurkError,
        attempt: Int,
    ): Duration? {
        if (attempt >= MAX_RETRIES) return null
        val backoff = BACKOFF_BASE * (1 shl attempt)
        return when {
            error is LurkError.RateLimited -> maxOf(backoff, error.retryAfter ?: Duration.ZERO)
            error is LurkError.Http && error.code >= SERVER_ERROR_FLOOR -> backoff
            else -> null
        }
    }

    private suspend fun send(
        body: String,
        token: String?,
        integrity: String?,
        clientId: String? = null,
        device: DeviceId = deviceId,
    ): GqlReply {
        val twitch = config.value.twitch
        val id = device.get()
        val text =
            http
                .post(twitch.gqlUrl) {
                    header("Client-Id", clientId ?: twitch.gqlClientId)
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

/**
 * Decodes the reply's data. A reply that can't be decoded is a schema change only when the server reported nothing
 * wrong: one that listed errors (a "service timeout" with `null` fields, say) failed on the server's side this time,
 * so it is [LurkError.Unexpected], which can be asked again.
 */
@Suppress("TooGenericExceptionCaught")
private fun <T> decodeReply(
    reply: GqlReply,
    decode: (JsonObject) -> T,
): T {
    val data =
        when (reply) {
            is GqlReply.Data -> reply
            GqlReply.AuthExpired -> throw LurkException(LurkError.AuthExpired)
            GqlReply.IntegrityFailed -> throw LurkException(LurkError.IntegrityRequired)
            GqlReply.ServerError -> throw LurkException(LurkError.Unexpected)
            GqlReply.UnexpectedShape -> throw LurkException(LurkError.SchemaChanged)
        }
    return try {
        decode(data.data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: LurkException) {
        throw e
    } catch (e: Exception) {
        throw LurkException(if (data.failed) LurkError.Unexpected else LurkError.SchemaChanged, e)
    }
}

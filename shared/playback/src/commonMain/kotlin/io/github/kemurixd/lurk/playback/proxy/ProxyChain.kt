package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

/** Result of walking a proxy list. */
sealed interface ChainResult<out T> {
    data class Success<T>(
        val value: T,
        val endpoint: ProxyEndpoint,
    ) : ChainResult<T>

    /** [lastError] is null when there was nothing to try. */
    data class AllFailed(
        val lastError: LurkError?,
    ) : ChainResult<Nothing>
}

private const val HTTP_PROXY_AUTH_REQUIRED = 407
private val HTTP_SERVER_ERRORS = 500..599

/** Offline, timeout, proxy error, block, 407 and 5xx mean the proxy is at fault. Anything else is the stream's. */
internal fun LurkError.blamesProxy(): Boolean =
    when (this) {
        LurkError.Offline, LurkError.Timeout, LurkError.ProxyFailed, LurkError.Blocked -> true
        is LurkError.Http -> code == HTTP_PROXY_AUTH_REQUIRED || code in HTTP_SERVER_ERRORS
        else -> false
    }

/** Tries proxies in order and reports each result to [health]. */
class ProxyChain(
    private val health: ProxyHealth,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    /**
     * Returns the first endpoint whose [attempt] succeeds. Endpoints in [skip] are never tried. Unhealthy ones
     * are passed over, unless every candidate is unhealthy, in which case all are tried once.
     *
     * A failure that is about the stream (404, 403, anything not blamed on the proxy) ends the walk at once as
     * [ChainResult.AllFailed] and does not count against the proxy. If [attempt] throws, that proxy is marked
     * failed with [LurkError.ProxyFailed] and the walk goes on, since a proxy problem must never stop playback.
     * Cancellation is rethrown.
     */
    suspend fun <T> first(
        endpoints: List<ProxyEndpoint>,
        skip: Set<ProxyEndpoint> = emptySet(),
        attempt: suspend (ProxyEndpoint) -> Outcome<T>,
    ): ChainResult<T> {
        val candidates = endpoints.filter { it !in skip }
        val order = candidates.filter(health::isHealthy).ifEmpty { candidates }
        var lastError: LurkError? = null
        var result: ChainResult<T>? = null
        for (endpoint in order) {
            val mark = timeSource.markNow()
            when (val outcome = attemptSafely(endpoint, attempt)) {
                is Outcome.Success -> {
                    health.recordSuccess(endpoint, mark.elapsedNow())
                    result = ChainResult.Success(outcome.value, endpoint)
                }

                is Outcome.Failure -> {
                    if (outcome.error.blamesProxy()) {
                        health.recordFailure(endpoint)
                        lastError = outcome.error
                    } else {
                        result = ChainResult.AllFailed(outcome.error)
                    }
                }
            }
            if (result != null) break
        }
        return result ?: ChainResult.AllFailed(lastError)
    }

    private suspend fun <T> attemptSafely(
        endpoint: ProxyEndpoint,
        attempt: suspend (ProxyEndpoint) -> Outcome<T>,
    ): Outcome<T> =
        try {
            attempt(endpoint)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            Outcome.Failure(LurkError.ProxyFailed)
        }
}

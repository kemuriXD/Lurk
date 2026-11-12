package io.github.kemurixd.lurk.platform.kick.api

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.network.ChallengeKind
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

private const val APEX_LABELS = 2

/**
 * Clears Cloudflare blocks on Kick requests. A blocked call asks the solver once, stores the cookies it returns
 * and replays the call once. Calls blocked together share one solve: whoever gets the lock first asks, the rest
 * see that an attempt finished after their call began and reuse its result. Calls that are not blocked never touch
 * the lock, so a slow solve holds up only the calls waiting on it.
 */
class CloudflareGate(
    private val solver: ChallengeSolver?,
    private val cookies: CookiesStorage,
) {
    private val lock = Mutex()

    /** Written only under [lock]; read without it at the start of every call. */
    @Volatile
    private var attempts = 0
    private var lastSolved = false

    suspend fun <T> guard(
        url: String,
        call: suspend () -> Outcome<T>,
    ): Outcome<T> {
        val attemptsBefore = attempts
        val first = call()
        if (first !is Outcome.Failure || first.error != LurkError.Blocked) return first
        return if (solveOnce(url, attemptsBefore)) call() else first
    }

    private suspend fun solveOnce(
        url: String,
        attemptsBefore: Int,
    ): Boolean =
        lock.withLock {
            if (attempts != attemptsBefore) return@withLock lastSolved
            val result = solve(url)
            if (result is ChallengeResult.Solved) store(url, result.cookies)
            attempts++
            lastSolved = result is ChallengeResult.Solved
            lastSolved
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun solve(url: String): ChallengeResult? =
        try {
            solver?.solve(ChallengeKind.CLOUDFLARE, url)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("SwallowedException") e: Exception,
        ) {
            ChallengeResult.Failed
        }

    private suspend fun store(
        url: String,
        values: Map<String, String>,
    ) {
        val target = Url(url)
        // Clearance cookies belong to the site, so the apex domain covers both kick.com and web.kick.com.
        val apex =
            target.host
                .split('.')
                .takeLast(APEX_LABELS)
                .joinToString(".")
        values.forEach { (name, value) ->
            cookies.addCookie(target, Cookie(name, value, domain = apex, path = "/"))
        }
    }

    override fun toString(): String = "CloudflareGate"
}

package io.github.kemurixd.lurk.core.network

/** Which anti-bot check stands in front of a request. */
enum class ChallengeKind { CLOUDFLARE, TWITCH_INTEGRITY }

/** What a [ChallengeSolver] managed to do. */
sealed interface ChallengeResult {
    /** The check passed; replay the request with these cookies and, if set, this user agent. */
    data class Solved(
        val cookies: Map<String, String>,
        val userAgent: String?,
    ) : ChallengeResult

    /** The check wants a person to interact with it. */
    data object NeedsUser : ChallengeResult

    data object Failed : ChallengeResult
}

/** Clears an anti-bot challenge, usually with a WebView. Implementations live in the app. */
fun interface ChallengeSolver {
    suspend fun solve(
        kind: ChallengeKind,
        url: String,
    ): ChallengeResult
}

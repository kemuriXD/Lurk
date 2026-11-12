package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.chat.ChatTransport
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.PlaybackGrant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** The grant's token flags as `key=value` pairs, or `flags=none` when the platform sends none. */
internal fun PlaybackGrant.flagsDetail(): String =
    tokenFlags?.let {
        "subscriber=${it.subscriber} turbo=${it.turbo} show_ads=${it.showAds} adFree=${it.adFree}"
    } ?: "flags=none"

/** A [LurkError] as a short status text. These carry no tokens. */
internal fun LurkError.detail(): String = "error=$this"

/** The first frame of [transport] that passes [accept] within [limit]: null on a timeout, an error on a throw. */
internal suspend fun firstFrame(
    transport: ChatTransport,
    limit: Duration,
    accept: (String) -> Boolean = { true },
): Outcome<String?> =
    try {
        Outcome.Success(withTimeoutOrNull(limit) { transport.frames().first(accept) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: LurkException) {
        Outcome.Failure(e.error)
    }

/** Reports a frame probe: [what] names the frame in the pass and timeout texts. */
internal fun SmokeReport.frameResult(
    check: String,
    result: Outcome<String?>,
    what: String,
    limit: Duration,
) {
    when (result) {
        is Outcome.Failure -> {
            fail(check, result.error.detail())
        }

        is Outcome.Success -> {
            if (result.value ==
                null
            ) {
                fail(check, "no $what in $limit")
            } else {
                pass(check, "first $what")
            }
        }
    }
}

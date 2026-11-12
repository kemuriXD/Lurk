package io.github.kemurixd.lurk.smoke

import kotlinx.coroutines.CancellationException

/**
 * Runs one check. An unexpected exception becomes `FAIL <check> error=<class name>`: the message and the trace are
 * never printed, since they can hold a token. Returns null when the check threw.
 */
internal suspend fun <T> SmokeReport.guarded(
    check: String,
    block: suspend () -> T,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        fail(check, "error=${e::class.simpleName}")
        null
    }

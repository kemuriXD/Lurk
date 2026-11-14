package io.github.kemurixd.lurk.feature.common

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val log = Logger.withTag("LoadBoth")

/**
 * Runs [call] on every platform at once and gives one [PlatformSlice] per platform. `Failure(NotSignedIn)` becomes
 * [PlatformSlice.NotSignedIn], any other failure or a thrown [LurkException] becomes [PlatformSlice.Failed]. Any
 * other exception is logged (class and message only) and becomes `Failed(Unexpected)`, so one platform's bug never
 * costs the other its result. Cancellation propagates.
 */
suspend fun <T> loadBoth(
    platforms: List<StreamingPlatform>,
    call: suspend (StreamingPlatform) -> Outcome<T>,
): Map<Platform, PlatformSlice<T>> =
    coroutineScope {
        platforms
            .map { platform -> async { platform.id to sliceOf(platform, call) } }
            .awaitAll()
            .toMap()
    }

private suspend fun <T> sliceOf(
    platform: StreamingPlatform,
    call: suspend (StreamingPlatform) -> Outcome<T>,
): PlatformSlice<T> {
    val outcome =
        try {
            call(platform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LurkException) {
            Outcome.Failure(e.error)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.w { "${platform.id} call failed unexpectedly: ${e::class.simpleName}: ${e.message}" }
            Outcome.Failure(LurkError.Unexpected)
        }
    return when (outcome) {
        is Outcome.Success -> PlatformSlice.Loaded(outcome.value)
        is Outcome.Failure -> sliceOf(outcome.error)
    }
}

private fun sliceOf(error: LurkError): PlatformSlice<Nothing> =
    if (error == LurkError.NotSignedIn) PlatformSlice.NotSignedIn else PlatformSlice.Failed(error)

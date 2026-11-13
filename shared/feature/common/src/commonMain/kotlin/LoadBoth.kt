package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Runs [call] on every platform at once and gives one [PlatformSlice] per platform. `Failure(NotSignedIn)` becomes
 * [PlatformSlice.NotSignedIn], any other failure or a thrown [LurkException] becomes [PlatformSlice.Failed].
 * Cancellation and unexpected exceptions propagate.
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
        } catch (e: LurkException) {
            Outcome.Failure(e.error)
        }
    return when (outcome) {
        is Outcome.Success -> PlatformSlice.Loaded(outcome.value)
        is Outcome.Failure -> sliceOf(outcome.error)
    }
}

private fun sliceOf(error: LurkError): PlatformSlice<Nothing> =
    if (error == LurkError.NotSignedIn) PlatformSlice.NotSignedIn else PlatformSlice.Failed(error)

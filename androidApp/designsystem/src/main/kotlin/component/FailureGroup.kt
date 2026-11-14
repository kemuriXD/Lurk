package io.github.kemurixd.lurk.designsystem.component

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform

/** Platforms that failed the same way, shown as one banner. */
data class FailureGroup(
    val platforms: List<Platform>,
    val error: LurkError,
)

/**
 * Groups failures that are the same error, in the order they first appear. Sign-in errors stay one group per platform,
 * since signing in is done for one platform at a time.
 */
fun List<Pair<Platform, LurkError>>.groupedByError(): List<FailureGroup> {
    val groups = mutableListOf<FailureGroup>()
    for ((platform, error) in this) {
        val at = if (error.isPerPlatform()) -1 else groups.indexOfFirst { it.error == error }
        if (at < 0) {
            groups += FailureGroup(listOf(platform), error)
        } else {
            groups[at] = groups[at].copy(platforms = groups[at].platforms + platform)
        }
    }
    return groups
}

private fun LurkError.isPerPlatform(): Boolean = this == LurkError.NotSignedIn || this == LurkError.AuthExpired

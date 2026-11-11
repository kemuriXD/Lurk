package io.github.kemurixd.lurk.core.storage

import kotlinx.serialization.Serializable

/** User settings. Crash reports stay off until the user opts in. */
@Serializable
data class AppSettings(
    val crashReportsOptIn: Boolean = false,
    val onboardingComplete: Boolean = false,
)

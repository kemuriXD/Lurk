package io.github.kemurixd.lurk

/** Version, commit and build date, shown on the launch screen now and on the About page later. */
object BuildInfo {
    val label: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA}, ${BuildConfig.BUILD_DATE})"
}

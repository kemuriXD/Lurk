plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    id("lurk.quality")
}

// Local-only check against the real Twitch and Kick endpoints. Never part of `check` or `allTests`.
tasks.register("liveSmoke") {
    group = "verification"
    description = "Runs the live platform smoke check (reads ~/Lurk-private/smoke.properties)."
    dependsOn(":tools:smoke:run")
}

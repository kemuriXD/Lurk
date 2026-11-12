import java.time.LocalDate
import java.time.ZoneOffset

plugins {
    id("lurk.android.application")
}

val gitSha: Provider<String> =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim().ifEmpty { "dev" } }
        .orElse("dev")

android {
    namespace = "io.github.kemurixd.lurk"

    defaultConfig {
        applicationId = "io.github.kemurixd.lurk"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_SHA", "\"${gitSha.get()}\"")
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now(ZoneOffset.UTC)}\"")
    }

    buildFeatures { buildConfig = true }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:storage"))
    implementation(project(":shared:core:config"))
    implementation(project(":shared:emotes"))
    implementation(project(":shared:chat"))
    implementation(project(":shared:playback"))
    implementation(libs.koin.android)
    // Dispatchers.Main on Android comes from this artifact at runtime.
    runtimeOnly(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

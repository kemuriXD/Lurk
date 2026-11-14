import java.time.LocalDate
import java.time.ZoneOffset

plugins {
    id("lurk.android.application")
    alias(libs.plugins.kotlin.serialization)
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

    buildTypes {
        // A release-like build for measuring performance on a device: R8, not debuggable, profileable from the shell.
        // Signed with the debug key and keeping the app ID, so it installs over a debug build and keeps its data.
        // Not the release signing setup.
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.splashscreen)
    implementation(libs.profileinstaller)
    // Composable names in system traces of the benchmark build, switched on per run with a broadcast.
    "benchmarkImplementation"(platform(libs.compose.bom))
    "benchmarkImplementation"(libs.compose.runtime.tracing)
    "benchmarkImplementation"(libs.tracing.perfetto.binary)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":androidApp:designsystem"))
    implementation(project(":androidApp:feature-ui:following"))
    implementation(project(":androidApp:feature-ui:browse"))
    implementation(project(":androidApp:feature-ui:search"))
    implementation(project(":androidApp:feature-ui:channel"))
    implementation(project(":androidApp:feature-ui:settings"))
    implementation(project(":shared:feature:common"))
    implementation(project(":shared:feature:following"))
    implementation(project(":shared:feature:browse"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:channel"))
    implementation(project(":shared:feature:accounts"))

    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:storage"))
    implementation(project(":shared:core:config"))
    implementation(project(":shared:emotes"))
    implementation(project(":shared:chat"))
    implementation(project(":shared:playback"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:platform:api"))
    implementation(project(":shared:platform:twitch"))
    implementation(project(":shared:platform:kick"))
    implementation(libs.koin.android)
    // Dispatchers.Main on Android comes from this artifact at runtime.
    runtimeOnly(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test-junit"))
    testImplementation(project(":shared:feature:testing"))
    testImplementation(libs.koin.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
}

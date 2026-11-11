plugins {
    id("lurk.android.application")
}

android {
    namespace = "io.github.kemurixd.lurk"

    defaultConfig {
        applicationId = "io.github.kemurixd.lurk"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}

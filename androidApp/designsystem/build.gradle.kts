plugins {
    id("lurk.android.library")
}

dependencies {
    api(project(":shared:feature:common"))
    implementation(libs.coil.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.test.core)
}

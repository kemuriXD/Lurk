plugins {
    id("lurk.android.library")
}

dependencies {
    implementation(project(":shared:feature:search"))
    implementation(project(":androidApp:designsystem"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.coil.compose)
}

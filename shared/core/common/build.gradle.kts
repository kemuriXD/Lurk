plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        api(libs.koin.core)
        api(libs.kermit)
    }
}

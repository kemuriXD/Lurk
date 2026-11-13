plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:common"))
            // Feature modules read StreamingPlatform and SessionRepository, which platform:api and auth expose.
            api(project(":shared:platform:api"))
            api(libs.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
    }
}

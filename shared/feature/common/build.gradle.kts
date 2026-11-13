plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:common"))
            // Kept off the API so UI modules never see platform or auth types. Feature modules declare their own.
            implementation(project(":shared:platform:api"))
            implementation(project(":shared:auth"))
            api(libs.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
    }
}

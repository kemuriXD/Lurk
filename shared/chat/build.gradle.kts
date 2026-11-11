plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":shared:core:model"))
        api(project(":shared:core:common"))
        api(project(":shared:emotes"))
    }
}

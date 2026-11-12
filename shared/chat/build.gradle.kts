plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":shared:core:model"))
        api(project(":shared:core:common"))
        api(project(":shared:core:network"))
        api(project(":shared:emotes"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.io.core)
    }
}

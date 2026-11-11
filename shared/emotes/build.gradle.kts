plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":shared:core:model"))
        api(project(":shared:core:common"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.io.core)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
    }
}

plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:platform:api"))
            api(project(":shared:core:network"))
            api(project(":shared:core:config"))
            api(project(":shared:core:storage"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.mock)
        }
    }
}

plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:platform:api"))
            implementation(project(":shared:core:network"))
            implementation(project(":shared:core:config"))
            implementation(project(":shared:core:storage"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.mock)
        }
    }
}

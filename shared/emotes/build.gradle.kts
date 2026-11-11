plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:common"))
            api(project(":shared:core:network"))
            api(project(":shared:core:storage"))
            api(project(":shared:core:config"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

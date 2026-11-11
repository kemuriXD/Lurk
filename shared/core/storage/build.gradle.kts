import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension

plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // The Keystore-backed secure store can only be tested on a real device or emulator.
    extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:common"))
            api(libs.sqldelight.runtime)
            api(libs.datastore.preferences.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("LurkDatabase") {
            packageName.set("io.github.kemurixd.lurk.core.storage.db")
        }
    }
}

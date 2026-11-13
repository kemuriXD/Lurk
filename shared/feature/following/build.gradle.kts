plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:feature:common"))
            // FollowingSort is part of the UI state and lives next to AppSettings.
            api(project(":shared:core:storage"))
            implementation(project(":shared:platform:api"))
            implementation(project(":shared:auth"))
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(project(":shared:feature:testing"))
            implementation(libs.koin.test)
        }
    }
}

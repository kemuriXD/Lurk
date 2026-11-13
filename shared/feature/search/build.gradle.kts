plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:feature:common"))
            implementation(project(":shared:platform:api"))
            implementation(project(":shared:auth"))
        }
        commonTest.dependencies {
            implementation(project(":shared:feature:testing"))
            implementation(libs.koin.test)
        }
    }
}

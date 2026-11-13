plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:feature:common"))
        }
        commonTest.dependencies {
            implementation(project(":shared:feature:testing"))
            implementation(libs.koin.test)
        }
    }
}

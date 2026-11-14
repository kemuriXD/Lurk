plugins {
    id("lurk.kmp.library")
}

// Fakes and stand-in builders. Used only as a commonTest dependency of the feature modules.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:feature:common"))
            api(project(":shared:platform:api"))
            api(project(":shared:auth"))
        }
    }
}

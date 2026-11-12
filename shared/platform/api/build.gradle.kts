plugins {
    id("lurk.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:common"))
            api(project(":shared:auth"))
            api(project(":shared:playback"))
            api(project(":shared:chat"))
            api(project(":shared:emotes"))
        }
    }
}

plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("lurkKmpLibrary") {
            id = "lurk.kmp.library"
            implementationClass = "LurkKmpLibraryPlugin"
        }
        register("lurkAndroidApplication") {
            id = "lurk.android.application"
            implementationClass = "LurkAndroidApplicationPlugin"
        }
        register("lurkQuality") {
            id = "lurk.quality"
            implementationClass = "LurkQualityPlugin"
        }
    }
}

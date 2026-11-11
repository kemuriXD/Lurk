plugins {
    id("lurk.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

// Bakes config/remote.json into the binary so the app always has a valid config, even offline on first launch.
val generateBundledConfig by tasks.registering {
    val source = rootProject.layout.projectDirectory.file("config/remote.json")
    val outputDir = layout.buildDirectory.dir("generated/bundledConfig/commonMain/kotlin")
    inputs.file(source).withPropertyName("remoteJson")
    outputs.dir(outputDir).withPropertyName("outputDir")
    doLast {
        val escaped =
            source.asFile
                .readText()
                .replace("\r\n", "\n")
                .trimEnd()
                .lines()
                .joinToString(" +\n") { line ->
                    val body =
                        line
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\$", "\\\$")
                    "    \"$body\\n\""
                }
        val file = outputDir.get().file("io/github/kemurixd/lurk/core/config/BundledRemoteConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from config/remote.json by :shared:core:config:generateBundledConfig. Do not edit.
            |package io.github.kemurixd.lurk.core.config
            |
            |internal const val BUNDLED_REMOTE_CONFIG: String =
            |$escaped
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledConfig)
            dependencies {
                api(project(":shared:core:network"))
                api(project(":shared:core:storage"))
            }
        }
        commonTest.dependencies {
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

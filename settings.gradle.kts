pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download the JDK 17 toolchain when it is missing. Settings plugins cannot use the version catalog.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Lurk"

include(":androidApp:app")
include(":shared:core:common")
include(":shared:core:config")
include(":shared:core:model")
include(":shared:core:network")
include(":shared:core:storage")
include(":shared:auth")
include(":shared:platform:api")
include(":shared:platform:twitch")
include(":shared:platform:kick")
include(":shared:chat")
include(":shared:emotes")
include(":shared:playback")
include(":tools:smoke")

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
include(":shared:feature:common")
include(":shared:feature:testing")
include(":shared:feature:following")
include(":shared:feature:browse")
include(":shared:feature:search")
include(":shared:feature:channel")
include(":shared:feature:accounts")
include(":androidApp:designsystem")
include(":androidApp:feature-ui:following")
include(":androidApp:feature-ui:browse")
include(":androidApp:feature-ui:search")
include(":androidApp:feature-ui:channel")
include(":androidApp:feature-ui:settings")
include(":tools:smoke")

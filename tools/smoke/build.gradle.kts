plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.kemurixd.lurk.smoke.MainKt")
}

dependencies {
    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:config"))
    implementation(project(":shared:core:storage"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:chat"))
    implementation(project(":shared:platform:api"))
    implementation(project(":shared:platform:twitch"))
    implementation(project(":shared:platform:kick"))
    implementation(project(":shared:playback"))
    implementation(libs.koin.core)
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// `./gradlew liveSmoke -Psmoke.file=<path>` points the check at another smoke file.
tasks.named<JavaExec>("run") {
    providers.gradleProperty("smoke.file").orNull?.let { systemProperty("smoke.file", it) }
    // A FAIL line is a result, not a build error: keep the output readable.
    isIgnoreExitValue = true
}

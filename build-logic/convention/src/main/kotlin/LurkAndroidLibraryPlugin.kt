import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android library module with Compose, Robolectric and Roborazzi, for the Android-only UI modules.
 * AGP 9 compiles Kotlin itself, so no Kotlin Android plugin is applied.
 */
class LurkAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<LibraryExtension> {
                namespace = PackageLayout.rootPackageFor(path)
                compileSdk = LurkSdk.COMPILE
                defaultConfig { minSdk = LurkSdk.MIN }
                buildFeatures { compose = true }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions { unitTests.isIncludeAndroidResources = true }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(LurkSdk.JDK)
            }

            // The screenshot baselines are inputs of the unit tests: changing one must rerun the check, never reuse an
            // earlier pass.
            tasks.withType<Test>().configureEach {
                inputs
                    .files(fileTree("src/test/snapshots"))
                    .withPropertyName("screenshotBaselines")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom))
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())

                add("testImplementation", platform(bom))
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
                add("testImplementation", libs.findLibrary("roborazzi").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
                add("debugImplementation", platform(bom))
                add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            }
        }
}

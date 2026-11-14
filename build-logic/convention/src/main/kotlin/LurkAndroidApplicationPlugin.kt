import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android app module with Compose and Robolectric Compose tests, set up like `lurk.android.library`.
 * AGP 9 compiles Kotlin itself, so no Kotlin Android plugin is applied.
 */
class LurkAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<ApplicationExtension> {
                compileSdk = LurkSdk.COMPILE
                defaultConfig {
                    minSdk = LurkSdk.MIN
                    targetSdk = LurkSdk.TARGET
                }
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

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("testImplementation", platform(bom))
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
                add("debugImplementation", platform(bom))
                add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            }
        }
}

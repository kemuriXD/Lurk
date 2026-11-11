import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Kotlin Multiplatform library with Android and JVM targets, for every module under `shared/`. */
class LurkKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    namespace = namespaceFor(path)
                    compileSdk = LurkSdk.COMPILE
                    minSdk = LurkSdk.MIN
                    withHostTest {}
                }
                jvm()
                jvmToolchain(17)

                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                    implementation(libs.findLibrary("turbine").get())
                }
            }
        }

    /** `:shared:core:model` becomes `io.github.kemurixd.lurk.core.model`. */
    private fun namespaceFor(path: String): String {
        val segments = path.split(':').filter { it.isNotEmpty() }
        require(segments.firstOrNull() == "shared") { "lurk.kmp.library only applies under :shared, got $path" }
        return (listOf("io.github.kemurixd.lurk") + segments.drop(1)).joinToString(".")
    }
}

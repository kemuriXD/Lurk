import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File

/** Root-only lint setup: Spotless, detekt and the module boundary check. */
class LurkQualityPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            require(target == target.rootProject) { "lurk.quality is root-only" }
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

            pluginManager.apply("base")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("dev.detekt")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**")
                    ktlint(ktlintVersion)
                }
                kotlinGradle {
                    target("**/*.kts")
                    targetExclude("**/build/**")
                    ktlint(ktlintVersion)
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                config.from(file("config/detekt/detekt.yml"))
                source.setFrom(
                    fileTree(projectDir) {
                        include("**/src/**/*.kt")
                        exclude("**/build/**")
                    },
                )
            }
            dependencies.add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())

            val checkModuleBoundaries = tasks.register<CheckModuleBoundaries>("checkModuleBoundaries")
            val checkPackageLayout =
                tasks.register<CheckPackageLayout>("checkPackageLayout") {
                    allprojects.forEach { module ->
                        val src = module.file("src")
                        srcRoots.put(src.absolutePath, PackageLayout.rootPackageFor(module.path))
                        sources.from(
                            fileTree(src) {
                                CheckPackageLayout.SOURCE_FOLDERS.forEach { include("*/$it/**/*.kt") }
                            },
                        )
                    }
                    marker.set(layout.buildDirectory.file("checkPackageLayout/ok"))
                    rootDir.set(layout.projectDirectory)
                }
            tasks.named("check") {
                dependsOn(checkModuleBoundaries, checkPackageLayout, "spotlessCheck", "detekt")
            }
            Unit
        }
}

/** Fails when any project depends on another one in a way [ModuleRules] forbids. */
abstract class CheckModuleBoundaries : DefaultTask() {
    init {
        group = "verification"
        description = "Checks project dependencies against the module boundary rules."
        // Reads other projects' configurations at execution time.
        notCompatibleWithConfigurationCache("Inspects the dependencies of every project")
    }

    @TaskAction
    fun check() {
        val broken = ModuleBoundaryWalker.violations(project.allprojects)
        if (broken.isNotEmpty()) {
            throw GradleException("Module boundary violations:\n" + broken.joinToString("\n") { " - $it" })
        }
    }
}

/**
 * Fails when a Kotlin file's package does not match its folder, as [PackageLayout] describes.
 * Checks `src/<sourceSet>/kotlin` and `src/<sourceSet>/java` in every project.
 */
abstract class CheckPackageLayout : DefaultTask() {
    /** Each project's `src` folder, keyed by absolute path, mapped to that project's root package. */
    @get:Input
    abstract val srcRoots: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    /** Only used to shorten paths in messages. */
    @get:Internal
    abstract val rootDir: DirectoryProperty

    init {
        group = "verification"
        description = "Checks that each Kotlin file's package matches its folder under the module's root package."
    }

    @TaskAction
    fun check() {
        val broken =
            srcRoots.get().flatMap { (srcPath, rootPackage) ->
                File(srcPath)
                    .listFiles()
                    .orEmpty()
                    .flatMap { sourceSet -> SOURCE_FOLDERS.map { sourceSet.resolve(it) } }
                    .filter { it.isDirectory }
                    .flatMap { dir ->
                        val label = dir.relativeTo(rootDir.get().asFile).invariantSeparatorsPath
                        PackageLayout.scan(dir, rootPackage).map { "$label: $it" }
                    }
            }
        if (broken.isNotEmpty()) {
            throw GradleException("Package layout violations:\n" + broken.joinToString("\n") { " - $it" })
        }
        marker.get().asFile.writeText("ok\n")
    }

    companion object {
        val SOURCE_FOLDERS = listOf("kotlin", "java")
    }
}

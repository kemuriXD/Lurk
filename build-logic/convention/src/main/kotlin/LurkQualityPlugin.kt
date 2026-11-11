import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

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
            tasks.named("check") {
                dependsOn(checkModuleBoundaries, "spotlessCheck", "detekt")
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
        val broken =
            project.allprojects
                .flatMap { source ->
                    source.configurations
                        .filter {
                            it.name.endsWith("implementation", ignoreCase = true) ||
                                it.name.endsWith("api", ignoreCase = true)
                        }.flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                        .mapNotNull { ModuleRules.violations(source.path, it.path) }
                }.distinct()
        if (broken.isNotEmpty()) {
            throw GradleException("Module boundary violations:\n" + broken.joinToString("\n") { " - $it" })
        }
    }
}

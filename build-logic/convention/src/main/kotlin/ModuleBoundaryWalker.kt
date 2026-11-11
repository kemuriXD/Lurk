import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/** Walks project dependencies and reports the ones [ModuleRules] forbids. */
object ModuleBoundaryWalker {
    private val checkedSuffixes = listOf("implementation", "api")

    /** Returns one message per distinct forbidden `implementation`/`api` project dependency. */
    fun violations(projects: Iterable<Project>): List<String> =
        projects
            .flatMap { source ->
                source.configurations
                    .filter { config -> checkedSuffixes.any { config.name.endsWith(it, ignoreCase = true) } }
                    .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                    .mapNotNull { ModuleRules.violations(source.path, it.path) }
            }.distinct()
}

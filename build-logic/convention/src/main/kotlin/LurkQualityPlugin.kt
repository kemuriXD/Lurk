import org.gradle.api.Plugin
import org.gradle.api.Project

/** Root-only lint setup (Spotless and detekt). Filled in by the quality task. */
class LurkQualityPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "lurk.quality is root-only" }
    }
}

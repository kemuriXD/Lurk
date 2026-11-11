import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBoundaryWalkerTest {
    private fun tree(): Triple<Project, Project, Project> {
        val root = ProjectBuilder.builder().withName("root").build()
        val shared =
            ProjectBuilder
                .builder()
                .withName("shared")
                .withParent(root)
                .build()
        val core =
            ProjectBuilder
                .builder()
                .withName("core")
                .withParent(shared)
                .build()
        val common =
            ProjectBuilder
                .builder()
                .withName("common")
                .withParent(core)
                .build()
        val playback =
            ProjectBuilder
                .builder()
                .withName("playback")
                .withParent(shared)
                .build()
        listOf(common, playback).forEach { it.pluginManager.apply("java-library") }
        return Triple(common, playback, root)
    }

    @Test
    fun `reports a core module depending on a non-core module`() {
        val (common, playback, root) = tree()
        common.dependencies.add("implementation", common.dependencies.project(mapOf("path" to playback.path)))

        val found = ModuleBoundaryWalker.violations(root.allprojects)

        assertEquals(1, found.size, found.toString())
        assertTrue(":shared:core:common" in found.single() && ":shared:playback" in found.single())
        assertTrue("core modules may depend only on other core modules" in found.single())
    }

    @Test
    fun `reports violations declared on source set configurations and api`() {
        val (common, playback, root) = tree()
        common.configurations.create("commonMainImplementation")
        common.dependencies.add("commonMainImplementation", common.dependencies.project(mapOf("path" to playback.path)))

        assertEquals(1, ModuleBoundaryWalker.violations(root.allprojects).size)

        val (common2, playback2, root2) = tree()
        common2.dependencies.add("api", common2.dependencies.project(mapOf("path" to playback2.path)))
        assertEquals(1, ModuleBoundaryWalker.violations(root2.allprojects).size)
    }

    @Test
    fun `accepts an allowed dependency`() {
        val (common, playback, root) = tree()
        playback.dependencies.add("implementation", playback.dependencies.project(mapOf("path" to common.path)))

        assertEquals(emptyList(), ModuleBoundaryWalker.violations(root.allprojects))
    }
}

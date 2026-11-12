import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageLayoutTest {
    @Test
    fun sharedModulePackageFollowsItsPath() {
        assertEquals("io.github.kemurixd.lurk.core.model", PackageLayout.rootPackageFor(":shared:core:model"))
        assertEquals("io.github.kemurixd.lurk.playback", PackageLayout.rootPackageFor(":shared:playback"))
    }

    @Test
    fun appModuleUsesTheBarePackage() {
        assertEquals("io.github.kemurixd.lurk", PackageLayout.rootPackageFor(":androidApp:app"))
    }

    @Test
    fun otherAndroidModulesFollowTheirPath() {
        assertEquals("io.github.kemurixd.lurk.designsystem", PackageLayout.rootPackageFor(":androidApp:designsystem"))
    }

    @Test
    fun fileAtSourceRootMustUseTheModulePackage() {
        assertNull(
            PackageLayout.violation("Tokenizer.kt", "io.github.kemurixd.lurk.chat", "io.github.kemurixd.lurk.chat"),
        )
    }

    @Test
    fun subfolderAddsToThePackage() {
        assertNull(
            PackageLayout.violation(
                "kick/KickParser.kt",
                "io.github.kemurixd.lurk.chat.kick",
                "io.github.kemurixd.lurk.chat",
            ),
        )
    }

    @Test
    fun packageThatDoesNotMatchTheFolderIsReported() {
        assertNotNull(
            PackageLayout.violation(
                "kick/KickParser.kt",
                "io.github.kemurixd.lurk.chat.twitch",
                "io.github.kemurixd.lurk.chat",
            ),
        )
    }

    @Test
    fun oldDeepLayoutIsReported() {
        assertNotNull(
            PackageLayout.violation(
                "io/github/kemurixd/lurk/chat/Tokenizer.kt",
                "io.github.kemurixd.lurk.chat",
                "io.github.kemurixd.lurk.chat",
            ),
        )
    }

    @Test
    fun missingPackageIsReported() {
        assertNotNull(PackageLayout.violation("Tokenizer.kt", null, "io.github.kemurixd.lurk.chat"))
    }

    @Test
    fun readsThePackageLineAfterComments() {
        val source = "/* licence */\n// note\n\npackage io.github.kemurixd.lurk.chat\n\nimport kotlin.test.Test\n"
        assertEquals("io.github.kemurixd.lurk.chat", PackageLayout.declaredPackage(source))
    }

    @Test
    fun readsNoPackageWhenThereIsNone() {
        assertNull(PackageLayout.declaredPackage("fun main() {}\n"))
    }

    @Test
    fun hyphensInModuleNamesAreDropped() {
        assertEquals("io.github.kemurixd.lurk.chatrenderer", PackageLayout.rootPackageFor(":androidApp:chat-renderer"))
        assertEquals(
            "io.github.kemurixd.lurk.featureui.player",
            PackageLayout.rootPackageFor(":androidApp:feature-ui:player"),
        )
    }

    @Test
    fun readsThePackageAfterFileAnnotationsAndKdoc() {
        val source = "/**\n * package not.this\n */\n@file:JvmName(\"X\")\n\npackage io.github.kemurixd.lurk.chat\n"
        assertEquals("io.github.kemurixd.lurk.chat", PackageLayout.declaredPackage(source))
    }

    @Test
    fun ignoresPackageLinesAfterTheFirstDeclaration() {
        val source = "fun f() = \"\"\"\npackage not.this\n\"\"\"\n"
        assertNull(PackageLayout.declaredPackage(source))
    }

    @Test
    fun stripsBackticksFromThePackage() {
        assertEquals(
            "io.github.kemurixd.lurk.fun",
            PackageLayout.declaredPackage("package io.github.kemurixd.lurk.`fun`\n"),
        )
    }

    @Test
    fun scanReportsOnlyMisplacedFiles() {
        val kotlinDir = createTempDirectory().toFile()
        try {
            kotlinDir.resolve("Good.kt").writeText("package io.github.kemurixd.lurk.chat\n")
            kotlinDir.resolve("kick").mkdirs()
            kotlinDir.resolve("kick/Good.kt").writeText("package io.github.kemurixd.lurk.chat.kick\n")
            kotlinDir.resolve("kick/Bad.kt").writeText("package io.github.kemurixd.lurk.chat\n")
            kotlinDir.resolve("notes.txt").writeText("package nothing\n")

            val found = PackageLayout.scan(kotlinDir, "io.github.kemurixd.lurk.chat")

            assertEquals(1, found.size)
            assertTrue("kick/Bad.kt" in found.single())
        } finally {
            kotlinDir.deleteRecursively()
        }
    }
}

import java.io.File

/**
 * Source folders leave out each module's root package, as the Kotlin coding conventions recommend.
 * A file at `src/commonMain/kotlin/kick/KickParser.kt` in `:shared:chat` declares
 * `package io.github.kemurixd.lurk.chat.kick`.
 */
object PackageLayout {
    private const val BASE = "io.github.kemurixd.lurk"
    private const val APP = ":androidApp:app"
    private val packageLine = Regex("""^package\s+([\w.`]+)""")

    /**
     * `:shared:core:model` becomes `io.github.kemurixd.lurk.core.model` and `:androidApp:app` is the bare package.
     * Hyphens are dropped, since `chat-renderer` is not a valid package name.
     */
    fun rootPackageFor(projectPath: String): String {
        if (projectPath == APP) return BASE
        val segments =
            projectPath
                .split(':')
                .filter { it.isNotEmpty() }
                .drop(1)
                .map { it.replace("-", "") }
        return (listOf(BASE) + segments).joinToString(".")
    }

    /**
     * The package a source declares, or null when it has none. Only the file header is read: comments,
     * blank lines and `@file:` annotations are skipped, and the first other line must be the package line.
     */
    fun declaredPackage(source: String): String? {
        var inBlockComment = false
        for (raw in source.lineSequence()) {
            val line = raw.trim()
            when {
                inBlockComment -> {
                    inBlockComment = !line.contains("*/")
                }

                line.startsWith("/*") -> {
                    inBlockComment = !line.contains("*/")
                }

                line.isEmpty() || line.startsWith("//") || line.startsWith("@file:") -> {
                    Unit
                }

                else -> {
                    return packageLine
                        .find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.replace("`", "")
                }
            }
        }
        return null
    }

    /**
     * Checks one file. [relativePath] is relative to its source folder and uses `/`.
     * Returns a message when the declared package does not match the folder, or null when it does.
     */
    fun violation(
        relativePath: String,
        declaredPackage: String?,
        rootPackage: String,
    ): String? {
        val folders = relativePath.split('/').dropLast(1)
        val expected = (listOf(rootPackage) + folders).joinToString(".")
        return when (declaredPackage) {
            expected -> null
            null -> "$relativePath has no package line, expected $expected"
            else -> "$relativePath declares $declaredPackage, expected $expected"
        }
    }

    /** Checks every `.kt` file under one source folder and returns one message per misplaced file. */
    fun scan(
        sourceDir: File,
        rootPackage: String,
    ): List<String> =
        sourceDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                violation(relative, declaredPackage(file.readText()), rootPackage)
            }.toList()
}

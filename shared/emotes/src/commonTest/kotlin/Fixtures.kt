package io.github.kemurixd.lurk.emotes

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/** Reads a file under `src/commonTest/fixtures`. Gradle runs JVM and host tests from the module directory. */
fun fixture(path: String): String =
    SystemFileSystem.source(Path("src/commonTest/fixtures/$path")).buffered().use { it.readString() }

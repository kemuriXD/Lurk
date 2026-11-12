package io.github.kemurixd.lurk.platform.twitch

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/** Reads a file under `src/commonTest/fixtures`. Gradle runs JVM and host tests from the module directory. */
fun fixture(path: String): String =
    SystemFileSystem.source(Path("src/commonTest/fixtures/$path")).buffered().use { it.readString() }

/** Reads every file directly inside a fixture directory as (name, text), sorted by name. */
fun fixtureDir(path: String): List<Pair<String, String>> =
    SystemFileSystem
        .list(Path("src/commonTest/fixtures/$path"))
        .map { it.name to fixture("$path/${it.name}") }
        .sortedBy { it.first }

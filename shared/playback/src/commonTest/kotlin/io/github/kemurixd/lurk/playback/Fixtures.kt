package io.github.kemurixd.lurk.playback

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/** Reads a file under `src/commonTest/fixtures`. Gradle runs JVM and host tests from the module directory. */
fun fixture(path: String): String =
    SystemFileSystem.source(Path("src/commonTest/fixtures/$path")).buffered().use { it.readString() }

/**
 * The stitched preroll as an earlier refresh showed it: cut after its third ad segment, so the live edge
 * (22:33:25.120 + 2.002 s) is inside the ad and a tracker reads the ad as playing.
 */
fun stitchedMidAd(): String {
    val out = ArrayList<String>()
    var segments = 0
    for (line in fixture("twitch/media-stitched-preroll.m3u8").lines()) {
        out += line
        if (line.isNotBlank() && !line.startsWith("#") && ++segments == 3) break
    }
    return out.joinToString("\n")
}

/** [stitchedMidAd] as a proxy serves it: the same span as live segments, with no stitched-ad marker. */
fun cleanMidAd(): String =
    stitchedMidAd()
        .lines()
        .filterNot { "twitch-stitched-ad" in it }
        .joinToString("\n") { if (it.startsWith("#EXTINF:")) it.substringBefore(',') + ",live" else it }

/** Reads every file directly inside a fixture directory as (name, text), sorted by name. */
fun fixtureDir(path: String): List<Pair<String, String>> =
    SystemFileSystem
        .list(Path("src/commonTest/fixtures/$path"))
        .map { it.name to fixture("$path/${it.name}") }
        .sortedBy { it.first }

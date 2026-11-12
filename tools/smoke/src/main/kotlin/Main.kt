package io.github.kemurixd.lurk.smoke

import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

private const val FILE_PROPERTY = "smoke.file"
private const val FILE_ENV = "LURK_SMOKE_FILE"

/** `~/Lurk-private/smoke.properties`, unless `-Dsmoke.file` or `LURK_SMOKE_FILE` names another path. */
private fun smokeFile(): Path =
    (System.getProperty(FILE_PROPERTY) ?: System.getenv(FILE_ENV))?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), "Lurk-private", "smoke.properties")

/** Runs every check against the real endpoints and prints one line each. Exits non-zero when any check failed. */
fun main() {
    val config = SmokeConfig.load(smokeFile())
    val report = SmokeReport(config.secrets)
    SmokeGraph().use { graph ->
        runBlocking {
            TwitchChecks(graph, config, report).run()
            KickChecks(graph, config, report).run()
        }
    }
    // Backstop: a socket library thread must never keep the process alive after the checks.
    exitProcess(if (report.failures == 0) 0 else 1)
}

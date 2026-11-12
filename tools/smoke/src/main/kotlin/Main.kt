package io.github.kemurixd.lurk.smoke

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.kemurixd.lurk.core.common.ScrubbingLogWriter
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
    // Kermit output from the shared modules goes through the same scrubbing as the app's logs.
    Logger.setLogWriters(ScrubbingLogWriter(platformLogWriter()))
    val config = SmokeConfig.load(smokeFile())
    val report = SmokeReport(config.secrets)
    try {
        SmokeGraph().use { graph ->
            runBlocking {
                TwitchChecks(graph, config, report).run()
                KickChecks(graph, config, report).run()
            }
        }
    } finally {
        // Backstop: a socket library thread must never keep the process alive after the checks.
        exitProcess(if (report.failures == 0) 0 else 1)
    }
}

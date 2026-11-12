package io.github.kemurixd.lurk.smoke

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.kemurixd.lurk.core.common.ScrubbingLogWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

private const val FILE_PROPERTY = "smoke.file"
private const val FILE_ENV = "LURK_SMOKE_FILE"

/** `~/Lurk-private/smoke.properties`, unless `-Dsmoke.file` or `LURK_SMOKE_FILE` names another path. */
private fun smokeFile(): Path =
    (System.getProperty(FILE_PROPERTY) ?: System.getenv(FILE_ENV))?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), "Lurk-private", "smoke.properties")

/**
 * Loads the config and runs [body] under a fresh report. Any throw outside a guarded check prints
 * `FAIL smoke error=<class name>` (never a message or trace) and makes the exit code non-zero.
 */
internal fun runSmoke(
    sink: (String) -> Unit,
    setup: () -> SmokeConfig,
    body: suspend (SmokeConfig, SmokeReport) -> Unit,
): Int {
    var report = SmokeReport(emptyList(), sink)
    try {
        val config = setup()
        report = SmokeReport(config.secrets, sink)
        runBlocking { body(config, report) }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        report.fail("smoke", "error=${e::class.simpleName}")
    }
    return if (report.failures == 0) 0 else 1
}

/** Runs every check against the real endpoints and prints one line each. Exits non-zero when any check failed. */
fun main() {
    var code = 1
    try {
        code =
            runSmoke(::println, { SmokeConfig.load(smokeFile()) }) { config, report ->
                // Kermit output from the shared modules is masked, then scrubbed, like the app's logs.
                Logger.setLogWriters(MaskingLogWriter(ScrubbingLogWriter(platformLogWriter()), config.secrets))
                SmokeGraph().use { graph ->
                    TwitchChecks(graph, config, report).run()
                    KickChecks(graph, config, report).run()
                }
            }
    } finally {
        // Backstop: a socket library thread must never keep the process alive after the checks.
        exitProcess(code)
    }
}

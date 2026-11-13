package io.github.kemurixd.lurk

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.login.CookieJar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val log = Logger.withTag("Startup")

/**
 * Application-wide scope for background startup work. A failing child is reported through [onFailure]
 * and never takes the app or its sibling jobs down.
 */
fun startupScope(onFailure: (Throwable) -> Unit = { log.e(it) { "Startup task failed" } }): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> onFailure(e) })

/**
 * Session work at launch. A login the process died in may have left its session cookie in the WebView store, so every
 * cookie is cleared, beside the restore and never waited on, so the splash doesn't wait for it. [onRestored] runs
 * once [restore] ends, however it ends.
 */
fun CoroutineScope.startSessions(
    cookies: CookieJar,
    restore: suspend () -> Unit,
    onRestored: () -> Unit,
) {
    launch { cookies.clearAll() }
    launch {
        try {
            restore()
        } finally {
            onRestored()
        }
    }
}

package io.github.kemurixd.lurk

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val log = Logger.withTag("Startup")

/**
 * Application-wide scope for background startup work. A failing child is reported through [onFailure]
 * and never takes the app or its sibling jobs down.
 */
fun startupScope(onFailure: (Throwable) -> Unit = { log.e(it) { "Startup task failed" } }): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> onFailure(e) })

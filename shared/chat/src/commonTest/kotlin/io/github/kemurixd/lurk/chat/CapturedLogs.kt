package io.github.kemurixd.lurk.chat

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/** Runs [block] with every Kermit log line captured, attached throwable included, then restores the writers. */
internal fun capturedLogs(block: () -> Unit): List<String> {
    val lines = mutableListOf<String>()
    val capture =
        object : LogWriter() {
            override fun log(
                severity: Severity,
                message: String,
                tag: String,
                throwable: Throwable?,
            ) {
                lines += message + throwable?.let { " | " + it.message }.orEmpty()
            }
        }
    val previous = Logger.config.logWriterList
    Logger.setLogWriters(capture)
    try {
        block()
    } finally {
        Logger.setLogWriters(previous)
    }
    return lines
}

package io.github.kemurixd.lurk.core.common

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/** Wraps another writer so nothing secret reaches logcat, files or crash reports. */
class ScrubbingLogWriter(
    private val delegate: LogWriter,
) : LogWriter() {
    override fun isLoggable(
        tag: String,
        severity: Severity,
    ): Boolean = delegate.isLoggable(tag, severity)

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        delegate.log(severity, Scrubber.scrub(message), tag, throwable?.let(::scrubbed))
    }

    /** Scrubs the message but keeps the original stack trace and a scrubbed copy of the cause chain. */
    private fun scrubbed(throwable: Throwable): Throwable {
        val name = throwable::class.simpleName ?: "Throwable"
        val message = Scrubber.scrub("$name: ${throwable.message.orEmpty()}")
        val cause = throwable.cause?.takeIf { it !== throwable }?.let(::scrubbed)
        return RuntimeException(message, cause).also { it.stackTrace = throwable.stackTrace }
    }
}

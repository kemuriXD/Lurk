package io.github.kemurixd.lurk.smoke

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/** Drops the smoke file's literal secret values from a log line and its throwable before [delegate] sees them. */
internal class MaskingLogWriter(
    private val delegate: LogWriter,
    secrets: List<String>,
) : LogWriter() {
    private val secrets = secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }

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
        delegate.log(severity, mask(message), tag, throwable?.let(::masked))
    }

    private fun masked(throwable: Throwable): Throwable {
        val name = throwable::class.simpleName ?: "Throwable"
        val cause = throwable.cause?.takeIf { it !== throwable }?.let(::masked)
        return RuntimeException(mask("$name: ${throwable.message.orEmpty()}"), cause).also {
            it.stackTrace = throwable.stackTrace
        }
    }

    private fun mask(text: String) = secrets.fold(text) { acc, secret -> acc.replace(secret, "…") }
}

package io.github.kemurixd.lurk.core.common

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoggingTest {
    private class Entry(
        val severity: Severity,
        val message: String,
        val tag: String,
        val throwable: Throwable?,
    )

    private class CapturingWriter : LogWriter() {
        val entries = mutableListOf<Entry>()

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            entries += Entry(severity, message, tag, throwable)
        }
    }

    @Test
    fun scrubsMessageBeforeDelegating() {
        val capture = CapturingWriter()
        ScrubbingLogWriter(capture).log(Severity.Info, "oauth:abcd1234efgh5678ijkl9012", "irc", null)
        val entry = capture.entries.single()
        assertEquals("oauth:…", entry.message)
        assertEquals("irc", entry.tag)
        assertEquals(Severity.Info, entry.severity)
    }

    @Test
    fun scrubsThrowableMessage() {
        val capture = CapturingWriter()
        val failure = IllegalStateException("bad cf_clearance=Zx81kd02mXqP3")
        ScrubbingLogWriter(capture).log(Severity.Error, "failed", "net", failure)
        val forwarded = capture.entries.single().throwable
        assertFalse(forwarded?.message.orEmpty().contains("Zx81kd02mXqP3"))
        assertEquals(true, forwarded?.message?.contains("IllegalStateException"))
    }

    @Test
    fun loggedThrowableKeepsStackTrace() {
        val capture = CapturingWriter()
        val failure = IllegalStateException("outer", IllegalArgumentException("bad cf_clearance=Zx81kd02mXqP3"))
        ScrubbingLogWriter(capture).log(Severity.Error, "failed", "net", failure)
        val forwarded = capture.entries.single().throwable
        assertEquals(failure.stackTrace.toList(), forwarded?.stackTrace?.toList())
        assertFalse(
            forwarded
                ?.cause
                ?.message
                .orEmpty()
                .contains("Zx81kd02mXqP3"),
        )
        assertEquals(true, forwarded?.cause?.message?.contains("IllegalArgumentException"))
    }
}

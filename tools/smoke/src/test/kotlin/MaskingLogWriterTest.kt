package io.github.kemurixd.lurk.smoke

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MaskingLogWriterTest {
    private val token = "short-secret"
    private val seen = mutableListOf<String>()
    private val sink =
        object : LogWriter() {
            override fun log(
                severity: Severity,
                message: String,
                tag: String,
                throwable: Throwable?,
            ) {
                seen += message + " " + throwable?.message.orEmpty()
            }
        }

    @Test
    fun literalTokenInAKermitLineIsMasked() {
        val writer = MaskingLogWriter(sink, listOf(token))
        writer.log(Severity.Info, "got $token here", "tag", IllegalStateException("also $token"))
        assertFalse(seen.single().contains(token))
        assertEquals(true, seen.single().contains("…"))
    }
}

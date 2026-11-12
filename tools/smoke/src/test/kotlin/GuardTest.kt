package io.github.kemurixd.lurk.smoke

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GuardTest {
    private val token = "tkn_Zq8vW3nLx0Rb7YpC5sHd2Ge"
    private val lines = mutableListOf<String>()
    private val report = SmokeReport(listOf(token), lines::add)

    @Test
    fun thrownMessageNeverReachesTheOutputAndLaterChecksRun() =
        runBlocking {
            report.guarded("first") { error("boom $token") }
            report.guarded("second") { report.pass("second", "ok") }
            assertEquals(listOf("FAIL first error=IllegalStateException", "PASS second ok"), lines)
            assertFalse(lines.joinToString().contains("boom"))
            assertEquals(1, report.failures)
        }
}

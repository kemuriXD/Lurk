package io.github.kemurixd.lurk.smoke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RunSmokeTest {
    private val token = "tkn_Zq8vW3nLx0Rb7YpC5sHd2Ge"
    private val lines = mutableListOf<String>()

    @Test
    fun crashOutsideACheckFailsTheRun() {
        val code = runSmoke(lines::add, { SmokeConfig(twitchToken = token) }) { _, _ -> error("boom $token") }
        assertEquals(1, code)
        assertEquals(listOf("FAIL smoke error=IllegalStateException"), lines)
    }

    @Test
    fun configParseFailureTakesTheSamePath() {
        val code = runSmoke(lines::add, { error("bad file $token") }) { _, _ -> }
        assertEquals(1, code)
        assertEquals(listOf("FAIL smoke error=IllegalStateException"), lines)
        assertFalse(lines.joinToString().contains(token))
    }

    @Test
    fun cleanRunExitsZero() {
        val code = runSmoke(lines::add, { SmokeConfig() }) { _, report -> report.pass("x") }
        assertEquals(0, code)
    }
}

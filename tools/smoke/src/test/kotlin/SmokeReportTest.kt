package io.github.kemurixd.lurk.smoke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SmokeReportTest {
    private val token = "tkn_Zq8vW3nLx0Rb7YpC5sHd2Ge"
    private val lines = mutableListOf<String>()
    private val report = SmokeReport(listOf(token), lines::add)

    @Test
    fun printsOneLinePerCheck() {
        report.pass("twitch.profile", "ok")
        report.fail("twitch.followed", "error=Offline")
        report.skip("kick.grant", "offline")
        assertEquals(
            listOf("PASS twitch.profile ok", "FAIL twitch.followed error=Offline", "SKIP kick.grant offline"),
            lines,
        )
    }

    @Test
    fun reportNeverPrintsToken() {
        report.pass("a", token)
        report.fail("b", "Authorization: OAuth $token")
        report.skip("c", "OAuth $token")
        report.pass("d", "https://example.test/path?token=$token&x=1")
        report.pass("e", "cookie $token;")
        val output = lines.joinToString("\n")
        // No window of six token characters may survive.
        token.windowed(6).forEach { piece -> assertFalse(piece in output, "leaked $piece") }
    }

    @Test
    fun scrubsPatternsEvenWhenTheValueIsUnknown() {
        report.pass("x", "https://example.test/a?sig=abcdef0123456789abcdef")
        assertFalse("abcdef0123456789abcdef" in lines.single())
    }
}

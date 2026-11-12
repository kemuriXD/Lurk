package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.core.common.Scrubber

/**
 * Prints one line per check: `PASS|FAIL|SKIP <check> <detail>`. Details carry flags, statuses and counts only. Every
 * line drops the literal [secrets] first, then goes through [Scrubber], so a token cannot reach the output even when
 * it shows up inside a header or a URL.
 */
class SmokeReport(
    secrets: List<String>,
    private val sink: (String) -> Unit = ::println,
) {
    private val secrets = secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }

    var failures: Int = 0
        private set

    fun pass(
        check: String,
        detail: String = "",
    ) = emit("PASS", check, detail)

    fun fail(
        check: String,
        detail: String = "",
    ) {
        failures++
        emit("FAIL", check, detail)
    }

    fun skip(
        check: String,
        detail: String = "",
    ) = emit("SKIP", check, detail)

    fun skipNoChannel(check: String) = skip(check, "no channel")

    private fun emit(
        status: String,
        check: String,
        detail: String,
    ) {
        val line = "$status $check $detail".trimEnd()
        sink(Scrubber.scrub(secrets.fold(line) { text, secret -> text.replace(secret, MASK) }))
    }

    private companion object {
        const val MASK = "…"
    }
}

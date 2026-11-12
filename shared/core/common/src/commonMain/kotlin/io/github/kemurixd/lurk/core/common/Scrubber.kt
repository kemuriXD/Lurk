package io.github.kemurixd.lurk.core.common

/** Removes credentials and tokens from text before it is logged or reported. */
object Scrubber {
    private const val MASK = "…"
    private const val KEEP_CHARS = 4

    private val urlUserinfo = Regex("""\b([A-Za-z][A-Za-z0-9+.-]*://)[^\s/?#@]+@""")
    private val urlQuery = Regex("""(https?://[^\s"'?]*)\?[^\s"']+""")
    private val oauthPrefix = Regex("""(oauth:)\S+""", RegexOption.IGNORE_CASE)
    private val authScheme = Regex("""\b(OAuth|Bearer) [A-Za-z0-9._~+/=-]{8,}""", RegexOption.IGNORE_CASE)
    private val headerLine = Regex("""\b(Set-Cookie|Cookie|Authorization):[^\r\n]*""", RegexOption.IGNORE_CASE)
    private val sensitivePair =
        Regex(
            """\b([\w.-]*(?:token|sig|session|clearance|auth|xsrf|csrf|secret|key)[\w.-]*)=[^\s&;,"']+""",
            RegexOption.IGNORE_CASE,
        )
    private val longRun = Regex("""[A-Za-z0-9_-]{24,}""")
    private val sha256Hex = Regex("""[0-9a-fA-F]{64}""")

    fun scrub(text: String): String {
        var out = urlUserinfo.replace(text) { "${it.groupValues[1]}$MASK@" }
        out = urlQuery.replace(out) { "${it.groupValues[1]}?$MASK" }
        out = oauthPrefix.replace(out) { "${it.groupValues[1]}$MASK" }
        out = authScheme.replace(out) { "${it.groupValues[1]} $MASK" }
        out = headerLine.replace(out) { "${it.groupValues[1]}: $MASK" }
        out = sensitivePair.replace(out) { "${it.groupValues[1]}=$MASK" }
        out = longRun.replace(out) { if (shouldMask(it.value)) it.value.take(KEEP_CHARS) + MASK else it.value }
        return out
    }

    private fun shouldMask(run: String): Boolean =
        !sha256Hex.matches(run) && run.any { it.isLetter() } && run.any { it.isDigit() }
}

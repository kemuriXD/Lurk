package io.github.kemurixd.lurk.playback.hls

private fun isNameChar(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

private fun indexOrEnd(
    list: String,
    ch: Char,
    from: Int,
): Int = list.indexOf(ch, from).let { if (it < 0) list.length else it }

/** Reads a value starting at [from]. Returns the value and the index after its trailing comma. */
private fun readValue(
    list: String,
    from: Int,
): Pair<String, Int> =
    if (from < list.length && list[from] == '"') {
        val close = indexOrEnd(list, '"', from + 1)
        list.substring(from + 1, close) to indexOrEnd(list, ',', close) + 1
    } else {
        val end = indexOrEnd(list, ',', from)
        list.substring(from, end).trim() to end + 1
    }

/**
 * Parses an HLS attribute list (`KEY=value,KEY="quoted, value"`). Quoted values may contain commas and
 * lose their quotes. A malformed pair is skipped, and this never throws.
 */
internal fun parseAttributes(list: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < list.length) {
        val eq = list.indexOf('=', i)
        if (eq < 0) break
        val comma = list.indexOf(',', i)
        if (comma in 0 until eq) {
            i = comma + 1 // key without a value
        } else {
            val key = list.substring(i, eq).trim()
            val (value, next) = readValue(list, eq + 1)
            i = next
            if (key.isNotEmpty() && key.all(::isNameChar)) out[key] = value
        }
    }
    return out
}

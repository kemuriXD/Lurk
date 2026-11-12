package io.github.kemurixd.lurk.chat.twitch

/**
 * One IRCv3 line. [tags] values are already unescaped; [params] holds the middle parameters followed by the
 * trailing one (the part after ` :`), if any.
 */
data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String,
    val params: List<String>,
)

/** A lenient IRCv3 line parser. It never throws: a blank or malformed line gives null. */
object IrcParser {
    private val commandPattern = Regex("[A-Z]+|[0-9]{3}")

    fun parse(line: String): IrcMessage? {
        val trimmed = line.trimEnd('\r', '\n').trimStart(' ')
        val (rawTags, afterTags) = if (trimmed.startsWith('@')) splitWord(trimmed.substring(1)) else "" to trimmed
        val (prefix, afterPrefix) =
            if (afterTags.startsWith(':')) splitWord(afterTags.substring(1)) else null to afterTags
        val (command, rest) = splitWord(afterPrefix)
        return if (commandPattern.matches(command)) {
            IrcMessage(parseTags(rawTags), prefix, command, parseParams(rest))
        } else {
            null
        }
    }

    /** Splits at the first space: the word before it, and the rest with leading spaces removed. */
    private fun splitWord(text: String): Pair<String, String> {
        val space = text.indexOf(' ')
        return if (space < 0) text to "" else text.substring(0, space) to text.substring(space + 1).trimStart(' ')
    }

    private fun parseTags(raw: String): Map<String, String> =
        raw
            .split(';')
            .filter { it.isNotEmpty() }
            .associate { tag ->
                val eq = tag.indexOf('=')
                if (eq < 0) tag to "" else tag.substring(0, eq) to unescape(tag.substring(eq + 1))
            }

    private fun parseParams(raw: String): List<String> {
        val params = mutableListOf<String>()
        var rest = raw.trimStart(' ')
        while (rest.isNotEmpty()) {
            if (rest.startsWith(':')) {
                params += rest.substring(1)
                break
            }
            val space = rest.indexOf(' ').takeIf { it >= 0 } ?: rest.length
            params += rest.substring(0, space)
            rest = rest.substring(space).trimStart(' ')
        }
        return params
    }

    /**
     * IRCv3 tag unescaping: `\s` space, `\:` `;`, `\\` `\`, `\r`, `\n`. Any other `\x` gives `x`; a trailing lone
     * `\` is dropped.
     */
    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\') {
                out.append(c)
            } else if (i + 1 < value.length) {
                i++
                out.append(
                    when (val next = value[i]) {
                        's' -> ' '
                        ':' -> ';'
                        'r' -> '\r'
                        'n' -> '\n'
                        else -> next
                    },
                )
            }
            i++
        }
        return out.toString()
    }
}

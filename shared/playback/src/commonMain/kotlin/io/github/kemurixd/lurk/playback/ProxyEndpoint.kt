package io.github.kemurixd.lurk.playback

/** Username and password for an authenticated proxy. Never printed. */
data class ProxyCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ProxyCredentials(…)"
}

/** A proxy the user configured. [toString] shows [label] only, so credentials and template paths never leak. */
sealed interface ProxyEndpoint {
    /** `host:port` for an engine proxy, the template's host for a template. */
    val label: String

    /** An `https://host/path/{channel}.m3u8` style service that returns the playlist directly. */
    data class UrlTemplate(
        val template: String,
    ) : ProxyEndpoint {
        override val label: String get() = hostOf(template)

        /** Replaces every `{channel}` and `$channel` placeholder with the URL-encoded [login]. */
        fun expand(login: String): String {
            val encoded = percentEncode(login)
            return template.replace(BRACE_PLACEHOLDER, encoded).replace(DOLLAR_PLACEHOLDER, encoded)
        }

        override fun toString(): String = "UrlTemplate($label)"

        companion object {
            private const val BRACE_PLACEHOLDER = "{channel}"
            private const val DOLLAR_PLACEHOLDER = "\$channel"

            /** True when [text] holds a `{channel}` or `$channel` placeholder, so it is a URL template. */
            fun hasPlaceholder(text: String): Boolean = BRACE_PLACEHOLDER in text || DOLLAR_PLACEHOLDER in text
        }
    }

    /** An HTTP proxy. HTTPS still travels through it with CONNECT. */
    data class Http(
        val host: String,
        val port: Int,
        val credentials: ProxyCredentials? = null,
    ) : ProxyEndpoint {
        override val label: String get() = "$host:$port"

        override fun toString(): String = "Http($label)"
    }

    companion object {
        private const val SCHEME_SEPARATOR = "://"
        private const val MAX_PORT = 65535

        /** Parses one line of user input. Never throws. */
        fun parse(input: String): ProxyParseResult {
            val text = input.trim()
            val schemeEnd = text.indexOf(SCHEME_SEPARATOR)
            return when {
                text.isEmpty() -> {
                    invalid(ProxyParseError.EMPTY)
                }

                schemeEnd <= 0 -> {
                    invalid(ProxyParseError.UNKNOWN_SCHEME)
                }

                else -> {
                    parseWithScheme(
                        text.substring(0, schemeEnd).lowercase(),
                        text.substring(schemeEnd + SCHEME_SEPARATOR.length),
                        text,
                    )
                }
            }
        }

        private fun parseWithScheme(
            scheme: String,
            rest: String,
            text: String,
        ): ProxyParseResult {
            val isWeb = scheme == "http" || scheme == "https"
            return when {
                isWeb && UrlTemplate.hasPlaceholder(text) -> ProxyParseResult.Valid(UrlTemplate(text))
                scheme == "https" -> invalid(ProxyParseError.TLS_PROXY_UNSUPPORTED)
                scheme == "http" -> parseHostPort(rest) { h, p, c -> Http(h, p, c) }
                else -> invalid(ProxyParseError.UNKNOWN_SCHEME)
            }
        }

        private fun parseHostPort(
            rest: String,
            build: (String, Int, ProxyCredentials?) -> ProxyEndpoint,
        ): ProxyParseResult {
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            val userInfo = authority.substringBeforeLast('@', "")
            val hostPort = authority.substringAfterLast('@')
            val colon =
                if (hostPort.startsWith("[")) {
                    hostPort.indexOf(':', hostPort.indexOf(']').coerceAtLeast(0))
                } else {
                    hostPort.lastIndexOf(':')
                }
            val host = if (colon < 0) hostPort else hostPort.substring(0, colon)
            val port = if (colon < 0) null else hostPort.substring(colon + 1).toIntOrNull()
            return when {
                host.isEmpty() || host.any { it.isWhitespace() } -> invalid(ProxyParseError.BAD_HOST)
                port == null || port !in 1..MAX_PORT -> invalid(ProxyParseError.BAD_PORT)
                else -> ProxyParseResult.Valid(build(host, port, credentialsOf(userInfo)))
            }
        }

        private fun credentialsOf(userInfo: String): ProxyCredentials? =
            userInfo.takeIf { it.isNotEmpty() }?.let {
                ProxyCredentials(
                    username = percentDecode(it.substringBefore(':')),
                    password = percentDecode(it.substringAfter(':', "")),
                )
            }

        private fun invalid(error: ProxyParseError) = ProxyParseResult.Invalid(error)
    }
}

/** Outcome of [ProxyEndpoint.parse]. */
sealed interface ProxyParseResult {
    data class Valid(
        val endpoint: ProxyEndpoint,
    ) : ProxyParseResult

    data class Invalid(
        val error: ProxyParseError,
    ) : ProxyParseResult
}

enum class ProxyParseError { EMPTY, UNKNOWN_SCHEME, TLS_PROXY_UNSUPPORTED, BAD_HOST, BAD_PORT }

internal fun hostOf(url: String): String =
    url
        .substringAfter("://")
        .takeWhile { it != '/' && it != '?' && it != '#' }
        .substringAfterLast('@')
        .substringBefore(':')

private const val HEX_RADIX = 16
private const val ESCAPE_LENGTH = 3

private fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val hex =
            if (text[i] == '%' && i + ESCAPE_LENGTH <= text.length) {
                text.substring(i + 1, i + ESCAPE_LENGTH).toIntOrNull(HEX_RADIX)
            } else {
                null
            }
        if (hex != null) {
            bytes.add(hex.toByte())
            i += ESCAPE_LENGTH
        } else {
            text[i].toString().encodeToByteArray().forEach(bytes::add)
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}

private const val UNRESERVED_MARKS = "-._~"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0xF
private const val BYTE_MASK = 0xFF
private const val ASCII_LIMIT = 0x80

private fun percentEncode(text: String): String {
    val out = StringBuilder(text.length)
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and BYTE_MASK
        val c = value.toChar()
        if (value < ASCII_LIMIT && (c.isLetterOrDigit() || c in UNRESERVED_MARKS)) {
            out.append(c)
        } else {
            out.append('%').append(HEX_DIGITS[value shr NIBBLE_BITS]).append(HEX_DIGITS[value and NIBBLE_MASK])
        }
    }
    return out.toString()
}

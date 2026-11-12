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

        override fun toString(): String = "UrlTemplate($label)"
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

    /** A SOCKS5 proxy. DNS is always resolved by the proxy. */
    data class Socks5(
        val host: String,
        val port: Int,
        val credentials: ProxyCredentials? = null,
    ) : ProxyEndpoint {
        override val label: String get() = "$host:$port"

        override fun toString(): String = "Socks5($label)"
    }

    companion object {
        private const val TEMPLATE_MARKER = "{channel}"
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
                isWeb && TEMPLATE_MARKER in text -> ProxyParseResult.Valid(UrlTemplate(text))
                scheme == "https" -> invalid(ProxyParseError.TLS_PROXY_UNSUPPORTED)
                scheme == "http" -> parseHostPort(rest) { h, p, c -> Http(h, p, c) }
                scheme == "socks5" || scheme == "socks5h" -> parseHostPort(rest) { h, p, c -> Socks5(h, p, c) }
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

private fun hostOf(url: String): String =
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

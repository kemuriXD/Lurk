package io.github.kemurixd.lurk.core.network

/**
 * A proxy that an engine routes through. [toString] shows `host:port` only, so credentials never reach a log.
 */
sealed interface EngineProxy {
    val host: String
    val port: Int
    val username: String?
    val password: String?

    /** An HTTP proxy. HTTPS targets travel through it with CONNECT. */
    data class Http(
        override val host: String,
        override val port: Int,
        override val username: String? = null,
        override val password: String? = null,
    ) : EngineProxy {
        override fun toString(): String = "Http($host:$port)"
    }

    /** A SOCKS5 proxy. The proxy resolves target hostnames. */
    data class Socks5(
        override val host: String,
        override val port: Int,
        override val username: String? = null,
        override val password: String? = null,
    ) : EngineProxy {
        override fun toString(): String = "Socks5($host:$port)"
    }
}

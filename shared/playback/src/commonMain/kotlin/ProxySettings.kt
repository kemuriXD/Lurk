package io.github.kemurixd.lurk.playback

enum class ProxyMode { OFF, ALWAYS, WHEN_ADS_DETECTED }

/** The user's proxy choice. [toString] stays safe because [ProxyEndpoint.toString] hides credentials. */
data class ProxySettings(
    val mode: ProxyMode = ProxyMode.OFF,
    val endpoints: List<ProxyEndpoint> = emptyList(),
) {
    val active: Boolean get() = mode != ProxyMode.OFF && endpoints.isNotEmpty()

    companion object {
        /** Prerolls arrive in the first playlist, so a configured list defaults to always. */
        fun defaultFor(endpoints: List<ProxyEndpoint>): ProxySettings =
            ProxySettings(if (endpoints.isEmpty()) ProxyMode.OFF else ProxyMode.ALWAYS, endpoints)
    }
}

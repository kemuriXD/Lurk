package io.github.kemurixd.lurk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.kemurixd.lurk.core.network.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online while a default network with an internet capability exists. Android's own reachability probe is left out of
 * the decision on purpose: a VPN, an enterprise Wi-Fi or a device without Google services can fail the probe on a
 * network that works. The first value is read from the active network at once, so a request in the first
 * milliseconds of a launch is judged by the real state. The callback lives as long as the app.
 */
class AndroidNetworkStatus(
    context: Context,
) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(currentlyOnline())

    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    online.value = hasInternet(capabilities)
                }

                // On a handover the old network may be reported lost after the new one is the default, so ask what
                // the default is now instead of assuming nothing is left.
                override fun onLost(network: Network) {
                    online.value = currentlyOnline()
                }
            },
        )
    }

    private fun currentlyOnline(): Boolean = hasInternet(manager.activeNetwork?.let(manager::getNetworkCapabilities))

    private fun hasInternet(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

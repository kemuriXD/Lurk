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
 * Online while the default network has a validated route to the internet. The first value is read from the active
 * network at once, so a request in the first milliseconds of a launch is judged by the real state, not a default.
 * The callback lives as long as the app.
 */
class AndroidNetworkStatus(
    context: Context,
) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(isValidated(manager.activeNetwork?.let(manager::getNetworkCapabilities)))

    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    online.value = isValidated(capabilities)
                }

                override fun onLost(network: Network) {
                    online.value = false
                }
            },
        )
    }

    private fun isValidated(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

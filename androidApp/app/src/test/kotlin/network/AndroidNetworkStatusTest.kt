package io.github.kemurixd.lurk.network

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidNetworkStatusTest {
    private companion object {
        const val LOST_NET_ID = 4242
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val shadow = shadowOf(manager)

    private fun capabilities(validated: Boolean): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return caps
    }

    private fun callback(): ConnectivityManager.NetworkCallback = shadow.networkCallbacks.single()

    @Test
    fun startsOnlineWhenTheActiveNetworkIsValidated() {
        shadow.setNetworkCapabilities(manager.activeNetwork, capabilities(validated = true))
        assertTrue(AndroidNetworkStatus(context).isOnline.value)
    }

    @Test
    fun startsOfflineWithoutAnActiveNetwork() {
        shadow.setDefaultNetworkActive(false)
        shadow.setActiveNetworkInfo(null)
        assertFalse(AndroidNetworkStatus(context).isOnline.value)
    }

    @Test
    fun anUnvalidatedNetworkStillCountsAsOnline() {
        shadow.setNetworkCapabilities(manager.activeNetwork, capabilities(validated = false))
        assertTrue(AndroidNetworkStatus(context).isOnline.value)
    }

    @Test
    fun startsOfflineWhenTheNetworkHasNoInternetCapability() {
        shadow.setNetworkCapabilities(manager.activeNetwork, ShadowNetworkCapabilities.newInstance())
        assertFalse(AndroidNetworkStatus(context).isOnline.value)
    }

    @Test
    fun losingTheOldNetworkDuringAHandoverStaysOnline() {
        val current = manager.activeNetwork!!
        shadow.setNetworkCapabilities(current, capabilities(validated = true))
        val status = AndroidNetworkStatus(context)
        // The new default network is already active when a different, older one is reported lost.
        callback().onLost(ShadowNetwork.newInstance(LOST_NET_ID))
        assertTrue(status.isOnline.value)
    }

    @Test
    fun losingTheNetworkThatIsStillReportedActiveGoesOffline() {
        val lost = manager.activeNetwork!!
        shadow.setNetworkCapabilities(lost, capabilities(validated = true))
        val status = AndroidNetworkStatus(context)
        // activeNetwork may still name the network being lost while onLost runs.
        callback().onLost(lost)
        assertFalse(status.isOnline.value)
    }

    @Test
    fun losingTheOnlyNetworkGoesOffline() {
        val only = manager.activeNetwork!!
        shadow.setNetworkCapabilities(only, capabilities(validated = true))
        val status = AndroidNetworkStatus(context)
        shadow.setDefaultNetworkActive(false)
        shadow.setActiveNetworkInfo(null)
        callback().onLost(only)
        assertFalse(status.isOnline.value)
    }

    @Test
    fun followsTheNetworkAsItComesAndGoes() {
        shadow.setNetworkCapabilities(manager.activeNetwork, ShadowNetworkCapabilities.newInstance())
        val status = AndroidNetworkStatus(context)
        val network: Network = manager.activeNetwork!!
        callback().onCapabilitiesChanged(network, capabilities(validated = true))
        assertTrue(status.isOnline.value)
        shadow.setDefaultNetworkActive(false)
        shadow.setActiveNetworkInfo(null)
        callback().onLost(network)
        assertFalse(status.isOnline.value)
        callback().onCapabilitiesChanged(network, ShadowNetworkCapabilities.newInstance())
        assertFalse(status.isOnline.value)
        callback().onCapabilitiesChanged(network, capabilities(validated = false))
        assertTrue(status.isOnline.value)
    }
}

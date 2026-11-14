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
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidNetworkStatusTest {
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
    fun startsOfflineWhenTheNetworkIsNotValidated() {
        shadow.setNetworkCapabilities(manager.activeNetwork, capabilities(validated = false))
        assertFalse(AndroidNetworkStatus(context).isOnline.value)
    }

    @Test
    fun followsTheNetworkAsItComesAndGoes() {
        shadow.setNetworkCapabilities(manager.activeNetwork, capabilities(validated = false))
        val status = AndroidNetworkStatus(context)
        val network: Network = manager.activeNetwork!!
        callback().onCapabilitiesChanged(network, capabilities(validated = true))
        assertTrue(status.isOnline.value)
        callback().onLost(network)
        assertFalse(status.isOnline.value)
        callback().onCapabilitiesChanged(network, capabilities(validated = false))
        assertFalse(status.isOnline.value)
        callback().onCapabilitiesChanged(network, capabilities(validated = true))
        assertTrue(status.isOnline.value)
    }
}

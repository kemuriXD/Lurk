package io.github.kemurixd.lurk.core.storage

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class KeystoreSecureStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("lurk_secure", Context.MODE_PRIVATE)
    private val store = KeystoreSecureStore(context)

    @Before
    @After
    fun wipe() = runTest { store.clear() }

    @Test
    fun putThenGetRoundTrips() =
        runTest {
            store.put("session", "example-value")

            assertEquals("example-value", store.get("session"))
        }

    @Test
    fun storedValueIsNotPlainText() =
        runTest {
            store.put("session", "example-value")

            assertFalse(prefs.getString("session", null)!!.contains("example-value"))
        }

    @Test
    fun removeMakesGetNull() =
        runTest {
            store.put("session", "example-value")

            store.remove("session")

            assertNull(store.get("session"))
        }

    @Test
    fun undecryptableEntryReturnsNull() =
        runTest {
            store.put("session", "example-value")
            val garbage = Base64.encodeToString(ByteArray(48) { it.toByte() }, Base64.NO_WRAP)
            prefs.edit().putString("session", garbage).commit()

            assertNull(store.get("session"))
            assertFalse(prefs.contains("session"))
        }
}

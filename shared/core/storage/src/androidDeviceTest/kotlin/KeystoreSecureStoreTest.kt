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
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun deletedKeyDropsEntry() =
        runTest {
            store.put("session", "example-value")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("lurk_secure_v1")

            assertNull(store.get("session"))
            assertFalse(prefs.contains("session"))
        }

    @Test
    fun malformedPayloadDropsEntry() =
        runTest {
            store.put("session", "example-value")
            prefs
                .edit()
                .putString("bad", "not base64 !!")
                .putString("short", "AAAA")
                .commit()

            assertNull(store.get("bad"))
            assertNull(store.get("short"))
            assertFalse(prefs.contains("bad"))
            assertFalse(prefs.contains("short"))
        }

    @Test
    fun transientKeystoreErrorKeepsEntry() =
        runTest {
            store.put("session", "example-value")
            val failing = KeystoreSecureStore(context, FailingKeySource(KeyStoreException("busy")))

            assertNull(failing.get("session"))
            assertTrue(prefs.contains("session"))
            assertEquals("example-value", store.get("session"))
        }

    @Test
    fun keystoreLoadIoErrorKeepsEntry() =
        runTest {
            store.put("session", "example-value")
            val failing = KeystoreSecureStore(context, FailingKeySource(IOException("load failed")))

            assertNull(failing.get("session"))
            assertTrue(prefs.contains("session"))
        }

    @Test
    fun unusableKeyKeepsEntry() =
        runTest {
            store.put("session", "example-value")
            val badKey = SecretKeySpec(ByteArray(5), "AES")
            val failing = KeystoreSecureStore(context, FixedKeySource(badKey))

            assertNull(failing.get("session"))
            assertTrue(prefs.contains("session"))
        }

    @Test
    fun putDoesNotRegenerateKeyOnTransientError() =
        runTest {
            val source = FailingKeySource(KeyStoreException("busy"))
            val failing = KeystoreSecureStore(context, source)

            assertFails { failing.put("session", "example-value") }
            assertEquals(0, source.created)
            assertFalse(prefs.contains("session"))
        }

    private class FailingKeySource(
        private val error: Exception,
    ) : KeySource {
        var created = 0

        override fun existing(): SecretKey? = throw error

        override fun create(): SecretKey {
            created++
            error("A transient failure must not generate a new key")
        }
    }

    private class FixedKeySource(
        private val key: SecretKey,
    ) : KeySource {
        override fun existing(): SecretKey = key

        override fun create(): SecretKey = key
    }
}

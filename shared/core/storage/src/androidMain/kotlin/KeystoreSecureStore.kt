package io.github.kemurixd.lurk.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStore] backed by an AES/GCM key in the Android Keystore. Each value is stored as Base64 of IV + ciphertext
 * in the `lurk_secure` SharedPreferences file. An entry that can never be decrypted again (tampered or malformed
 * data, a key that was invalidated or deleted) is dropped and reads as null. Other Keystore failures read as null but
 * keep the entry, and [put] throws instead of replacing the key.
 */
class KeystoreSecureStore internal constructor(
    context: Context,
    private val keys: KeySource,
) : SecureStore {
    constructor(context: Context) : this(context, AndroidKeystoreKeySource)

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyLock = Any()

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) {
            val stored = prefs.getString(key, null) ?: return@withContext null
            when (val result = decrypt(stored)) {
                is Decrypted.Value -> {
                    result.plain
                }

                Decrypted.Unrecoverable -> {
                    prefs.edit().remove(key).commit()
                    null
                }

                Decrypted.Unavailable -> {
                    null
                }
            }
        }

    override suspend fun put(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(key, encrypt(obtainKey(), value)).commit()
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).commit() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }

    private fun encrypt(
        secretKey: SecretKey,
        value: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val payload = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * Only a tampered payload, a missing or permanently invalidated key, or malformed stored data make an entry
     * [Decrypted.Unrecoverable]. Any other Keystore failure may be transient, so the entry is kept.
     */
    private fun decrypt(stored: String): Decrypted {
        val payload = decodeOrNull(stored)
        return if (payload == null || payload.size <= IV_BYTES) Decrypted.Unrecoverable else decryptPayload(payload)
    }

    private fun decodeOrNull(stored: String): ByteArray? =
        try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decryptPayload(payload: ByteArray): Decrypted =
        try {
            val secretKey = keys.existing()
            if (secretKey == null) {
                Decrypted.Unrecoverable
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
                Decrypted.Value(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES).decodeToString())
            }
        } catch (_: AEADBadTagException) {
            Decrypted.Unrecoverable
        } catch (_: KeyPermanentlyInvalidatedException) {
            Decrypted.Unrecoverable
        } catch (e: GeneralSecurityException) {
            unavailable(e)
        } catch (e: IOException) {
            unavailable(e)
        } catch (e: ProviderException) {
            unavailable(e)
        }

    private fun unavailable(error: Exception): Decrypted {
        log.w { "Secure store read failed (${error::class.simpleName}); keeping the entry" }
        return Decrypted.Unavailable
    }

    /**
     * Creates the key only when the alias is absent. Any other failure propagates, because a new key would orphan
     * every stored entry. Synchronised so two first-time writes cannot each generate a key.
     */
    private fun obtainKey(): SecretKey = synchronized(keyLock) { keys.existing() ?: keys.create() }

    private sealed interface Decrypted {
        /** Not a data class, so the plain text never ends up in a `toString`. */
        class Value(
            val plain: String,
        ) : Decrypted

        /** The entry can never be decrypted again and is removed. */
        data object Unrecoverable : Decrypted

        /** The Keystore failed in a way that may be transient; the entry is kept. */
        data object Unavailable : Decrypted
    }

    private companion object {
        const val PREFS_NAME = "lurk_secure"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        val log = Logger.withTag("SecureStore")
    }
}

/** Access to the store's AES key. Split out so tests can simulate Keystore failures. */
internal interface KeySource {
    /** The key, or null only when the alias does not exist. Any other failure is thrown. */
    fun existing(): SecretKey?

    /** Generates a new key under the alias. */
    fun create(): SecretKey
}

internal object AndroidKeystoreKeySource : KeySource {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lurk_secure_v1"
    private const val KEY_BITS = 256

    override fun existing(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw UnrecoverableKeyException("Alias exists but holds no secret key")
    }

    override fun create(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}

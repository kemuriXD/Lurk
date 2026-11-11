package io.github.kemurixd.lurk.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStore] backed by an AES/GCM key in the Android Keystore. Each value is stored as Base64 of IV + ciphertext
 * in the `lurk_secure` SharedPreferences file. An entry that can no longer be decrypted (tampered data, a key that
 * was invalidated or deleted) is dropped and reads as null.
 */
class KeystoreSecureStore(
    context: Context,
) : SecureStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyLock = Any()

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) {
            val stored = prefs.getString(key, null) ?: return@withContext null
            val plain = existingKey()?.let { decryptOrNull(it, stored) }
            if (plain == null) prefs.edit().remove(key).commit()
            plain
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

    /** Covers `AEADBadTagException`, `KeyPermanentlyInvalidatedException` and malformed payloads. */
    private fun decryptOrNull(
        secretKey: SecretKey,
        stored: String,
    ): String? =
        try {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            if (payload.size <= IV_BYTES) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
                cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES).decodeToString()
            }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return try {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey?
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    /** Synchronised so two first-time writes cannot each generate a key and orphan the other's entry. */
    private fun obtainKey(): SecretKey = synchronized(keyLock) { existingKey() ?: createKey() }

    private fun createKey(): SecretKey {
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

    private companion object {
        const val PREFS_NAME = "lurk_secure"
        const val KEY_ALIAS = "lurk_secure_v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

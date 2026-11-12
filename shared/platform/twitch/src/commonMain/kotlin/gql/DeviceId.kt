package io.github.kemurixd.lurk.platform.twitch.gql

import io.github.kemurixd.lurk.core.storage.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

private const val KEY = "twitch.deviceId"
private const val ID_BYTES = 16
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private val validId = Regex("[0-9a-f]{32}")

/** The `X-Device-Id` Twitch's web client sends: 32 lowercase hex characters, made once and kept. */
class DeviceId(
    private val secure: SecureStore,
) {
    private val lock = Mutex()
    private var cached: String? = null

    suspend fun get(): String =
        lock.withLock {
            cached ?: (secure.get(KEY)?.takeIf { validId.matches(it) } ?: create()).also { cached = it }
        }

    private suspend fun create(): String {
        val id =
            Random.nextBytes(ID_BYTES).joinToString("") {
                (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
            }
        secure.put(KEY, id)
        return id
    }
}

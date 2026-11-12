package io.github.kemurixd.lurk.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** [AppSettings] kept as one JSON value in Preferences DataStore. Missing or unreadable data reads as the defaults. */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs[KEY] = json.encodeToString(transform(prefs.toSettings())) }
    }

    suspend fun reset() {
        dataStore.edit { it.remove(KEY) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val raw = this[KEY] ?: return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(raw)
        } catch (_: SerializationException) {
            AppSettings()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("app_settings")
        val json = Json { ignoreUnknownKeys = true }
    }
}

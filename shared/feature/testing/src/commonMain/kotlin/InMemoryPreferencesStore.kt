package io.github.kemurixd.lurk.feature.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.kemurixd.lurk.core.storage.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A Preferences [DataStore] kept in memory, so `runTest` tests can use a real [SettingsStore] on virtual time. */
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}

/** A [SettingsStore] over [InMemoryPreferencesStore]. Pass the same instance to two view models to share settings. */
fun inMemorySettingsStore(): SettingsStore = SettingsStore(InMemoryPreferencesStore())

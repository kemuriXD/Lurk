package io.github.kemurixd.lurk.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {
    private val dir: File = createTempDirectory("settings").toFile()
    private val file = File(dir, "settings.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    /** DataStore allows one live instance per file, so each store gets its own scope that is cancelled before reuse. */
    private fun openStore(): Pair<SettingsStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(scope = scope) { file.toOkioPath() }
        return SettingsStore(dataStore) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    @Test
    fun firstEmissionIsTheDefaults() =
        runBlocking {
            val (store, _) = openStore()

            val settings = store.settings.first()

            assertEquals(AppSettings(), settings)
            assertFalse(settings.crashReportsOptIn)
        }

    @Test
    fun updatesSurviveAFreshStoreOnTheSameFile() =
        runBlocking {
            val (store, scope) = openStore()
            store.update { it.copy(onboardingComplete = true) }
            scope.coroutineContext[Job]!!.cancelAndJoin()

            val (reopened, _) = openStore()

            assertTrue(reopened.settings.first().onboardingComplete)
        }

    @Test
    fun resetBringsBackTheDefaults() =
        runBlocking {
            val (store, _) = openStore()
            store.update { it.copy(crashReportsOptIn = true, onboardingComplete = true) }

            store.reset()

            assertEquals(AppSettings(), store.settings.first())
        }
}

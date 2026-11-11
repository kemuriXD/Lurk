package io.github.kemurixd.lurk.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import io.github.kemurixd.lurk.core.common.commonModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Path.Companion.toOkioPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class StorageModuleTest {
    private val dir = createTempDirectory("storage-module").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun resolvesStoresGivenPlatformBindings() {
        val platform =
            module {
                single<SqlDriver> { createJvmDriver() }
                single<DataStore<Preferences>> {
                    PreferenceDataStoreFactory.createWithPath(scope = scope) {
                        dir.resolve("settings.preferences_pb").toOkioPath()
                    }
                }
            }
        val koin = koinApplication { modules(commonModule, storageModule, platform) }.koin

        assertNotNull(koin.get<CacheStore>())
        assertNotNull(koin.get<SettingsStore>())
        koin.get<SqlDriver>().close()
    }
}

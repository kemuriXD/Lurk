package io.github.kemurixd.lurk.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    private val dir: File = createTempDirectory("app-settings").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore =
        PreferenceDataStoreFactory.createWithPath(scope = scope) { File(dir, "settings.preferences_pb").toOkioPath() }

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.deleteRecursively()
    }

    /** Writes [json] the way an older build stored it, under the key `SettingsStore` reads. */
    private suspend fun storeRaw(json: String) {
        dataStore.edit { it[stringPreferencesKey("app_settings")] = json }
    }

    @Test
    fun oldJsonWithoutNewFieldsReadsDefaults() =
        runBlocking {
            storeRaw("""{"crashReportsOptIn":true,"onboardingComplete":true}""")

            val settings = SettingsStore(dataStore).settings.first()

            assertEquals(
                AppSettings(
                    crashReportsOptIn = true,
                    onboardingComplete = true,
                    followingFilter = PlatformFilterSetting.ALL,
                    followingSort = FollowingSortSetting.VIEWERS,
                    followingOfflineCollapsed = false,
                ),
                settings,
            )
        }

    @Test
    fun offlineFoldRoundTrips() =
        runBlocking {
            storeRaw("""{"followingSort":"NAME","followingOfflineCollapsed":true}""")

            val settings = SettingsStore(dataStore).settings.first()

            assertEquals(
                AppSettings(followingSort = FollowingSortSetting.NAME, followingOfflineCollapsed = true),
                settings,
            )
        }

    @Test
    fun unknownEnumValueFallsBackToItsDefaultAndKeepsTheRest() =
        runBlocking {
            storeRaw("""{"onboardingComplete":true,"followingFilter":"KICK","followingSort":"SOMETHING_NEW"}""")

            val settings = SettingsStore(dataStore).settings.first()

            assertEquals(
                AppSettings(
                    onboardingComplete = true,
                    followingFilter = PlatformFilterSetting.KICK,
                    followingSort = FollowingSortSetting.VIEWERS,
                ),
                settings,
            )
        }
}

package io.github.kemurixd.lurk.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import org.koin.dsl.module

private const val CACHE_DB_NAME = "lurk_cache.db"
private const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/** Android bindings for [storageModule]: the SQLite driver, the settings DataStore and the Keystore secure store. */
fun androidStorageModule(context: Context): Module =
    module {
        val appContext = context.applicationContext
        single<SqlDriver> { AndroidSqliteDriver(LurkDatabase.Schema, appContext, CACHE_DB_NAME) }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath {
                appContext.filesDir.resolve(SETTINGS_FILE_NAME).toOkioPath()
            }
        }
        single<SecureStore> { KeystoreSecureStore(appContext) }
    }

package io.github.kemurixd.lurk.di

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.commonModule
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.androidStorageModule
import io.github.kemurixd.lurk.core.storage.storageModule
import io.github.kemurixd.lurk.playback.playbackModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.TimeSource

@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    private val context: Context =
        object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }

    @Test
    fun commonModuleIsComplete() {
        commonModule.verify()
    }

    @Test
    fun networkModuleIsComplete() {
        // Koin inspects constructor parameters, which the factory and the monotonic clock fill in.
        networkModule.verify(extraTypes = listOf(HttpClientEngine::class, TimeSource.WithComparableMarks::class))
    }

    @Test
    fun storageModuleIsCompleteGivenPlatformBindings() {
        storageModule.verify(extraTypes = listOf(SqlDriver::class, DataStore::class, DispatcherProvider::class))
    }

    @Test
    fun androidStorageModuleIsComplete() {
        androidStorageModule(context).verify(extraTypes = listOf(Context::class, Preferences::class))
    }

    @Test
    fun configModuleIsCompleteGivenNetworkAndStorage() {
        configModule.verify(extraTypes = listOf(HttpClient::class, CacheStore::class, Clock::class))
    }

    @Test
    fun appModulesCoverEveryModule() {
        val modules: List<Module> = appModules(context)
        assertEquals(8, modules.size)
        assertTrue(playbackModule in modules)
    }
}

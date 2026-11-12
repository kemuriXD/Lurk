package io.github.kemurixd.lurk.di

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.auth.authModule
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.commonModule
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.core.storage.androidStorageModule
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.github.kemurixd.lurk.core.storage.storageModule
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.platform.kick.kickModule
import io.github.kemurixd.lurk.platform.twitch.twitchModule
import io.github.kemurixd.lurk.playback.PlaybackSource
import io.github.kemurixd.lurk.playback.playbackModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.TimeSource

private class MemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() = values.clear()
}

@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    private val context: Context =
        object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }

    private val platformExtraTypes =
        listOf(
            HttpClient::class,
            WebSocketConnector::class,
            SecureStore::class,
            DispatcherProvider::class,
            SessionRepository::class,
            StateFlow::class,
            CoroutineScope::class,
            // Optional: resolved with getOrNull().
            ChallengeSolver::class,
        )

    /** The app's real network, config, auth, platform and scope modules over in-memory storage. */
    private fun platformGraph() =
        koinApplication {
            modules(
                commonModule,
                networkModule,
                configModule,
                appScopeModule,
                authModule,
                twitchModule,
                kickModule,
                module {
                    single<SecureStore> { MemorySecureStore() }
                    single {
                        CacheStore(
                            LurkDatabase(
                                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LurkDatabase.Schema),
                            ),
                            get(),
                        )
                    }
                    single<Clock> { Clock.System }
                },
            )
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
        assertEquals(12, modules.size)
        assertTrue(playbackModule in modules)
        assertTrue(authModule in modules)
        assertTrue(twitchModule in modules)
        assertTrue(kickModule in modules)
        assertTrue(appScopeModule in modules)
    }

    // authModule takes a List of fetchers, which verify() cannot see through.
    // bothPlatformsAndSessionsResolve covers it.

    @Test
    fun twitchModuleIsComplete() {
        twitchModule.verify(extraTypes = platformExtraTypes)
    }

    @Test
    fun kickModuleIsComplete() {
        kickModule.verify(extraTypes = platformExtraTypes + CookiesStorage::class)
    }

    @Test
    fun playbackSourcesBound() {
        val koin = platformGraph().koin
        assertEquals(
            setOf(Platform.TWITCH, Platform.KICK),
            koin.getAll<PlaybackSource>().map { it.platform }.toSet(),
        )
    }

    @Test
    fun bothPlatformsAndSessionsResolve() {
        val koin = platformGraph().koin
        assertEquals(
            setOf(Platform.TWITCH, Platform.KICK),
            koin.getAll<StreamingPlatform>().map { it.id }.toSet(),
        )
        assertEquals(
            setOf(Platform.TWITCH, Platform.KICK),
            koin.getAll<ProfileFetcher>().map { it.platform }.toSet(),
        )
        koin.get<SessionRepository>()
    }

    @Test
    fun cloudflareGateAndClientShareOneCookieJar() {
        val koin = platformGraph().koin
        assertSame(koin.get<CookiesStorage>(), koin.get<CookiesStorage>())
    }
}

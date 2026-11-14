package io.github.kemurixd.lurk.di

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.auth.authModule
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.commonModule
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.AlwaysOnline
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.core.network.NetworkStatus
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.core.storage.androidStorageModule
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.github.kemurixd.lurk.core.storage.storageModule
import io.github.kemurixd.lurk.feature.accounts.AccountsViewModel
import io.github.kemurixd.lurk.feature.accounts.LoginViewModel
import io.github.kemurixd.lurk.feature.accounts.accountsModule
import io.github.kemurixd.lurk.feature.browse.CategoriesViewModel
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsViewModel
import io.github.kemurixd.lurk.feature.browse.TopLiveViewModel
import io.github.kemurixd.lurk.feature.browse.browseModule
import io.github.kemurixd.lurk.feature.channel.ChannelViewModel
import io.github.kemurixd.lurk.feature.channel.channelModule
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.common.featureCommonModule
import io.github.kemurixd.lurk.feature.following.FollowingViewModel
import io.github.kemurixd.lurk.feature.following.followingModule
import io.github.kemurixd.lurk.feature.search.SearchViewModel
import io.github.kemurixd.lurk.feature.search.searchModule
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.login.AndroidCookieJar
import io.github.kemurixd.lurk.login.CookieJar
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            NetworkStatus::class,
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
                platformsModule,
                featureCommonModule,
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
                    single<NetworkStatus> { AlwaysOnline }
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
        networkModule.verify(
            extraTypes =
                listOf(HttpClientEngine::class, TimeSource.WithComparableMarks::class, NetworkStatus::class),
        )
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
        assertEquals(21, modules.size)
        assertTrue(accountsModule in modules)
        assertTrue(loginModule in modules)
        assertTrue(featureCommonModule in modules)
        assertTrue(followingModule in modules)
        assertTrue(browseModule in modules)
        assertTrue(searchModule in modules)
        assertTrue(channelModule in modules)
        assertTrue(platformsModule in modules)
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
    fun featureCommonModuleResolvesOverTheRealPlatforms() {
        // No verify() here: LastLiveLoader takes List<StreamingPlatform>, and verify() still reports that parameter
        // missing with List::class in extraTypes ("Missing definition for ... type 'kotlin.collections.List'"). So the
        // module is resolved over the real graph instead, which checks every binding.
        val koin = platformGraph().koin
        koin.get<AccountsState>()
        koin.get<LastLiveLoader>()
    }

    // verify() can't check a generic List binding, so followingModule is covered by resolving it over the real graph.

    @Test
    fun platformListHoldsBothPlatforms() {
        val koin = platformGraph().koin
        assertEquals(setOf(Platform.TWITCH, Platform.KICK), koin.get<List<StreamingPlatform>>().map { it.id }.toSet())
    }

    @Test
    fun followingViewModelResolvesOverTheRealPlatforms() {
        val app = platformGraph()
        app.modules(followingModule, module { single { inMemorySettingsStore() } })
        app.koin.get<FollowingViewModel>()
    }

    @Test
    fun browseViewModelsResolveOverTheRealPlatforms() {
        val app = platformGraph()
        app.modules(browseModule)
        app.koin.get<TopLiveViewModel>()
        app.koin.get<CategoriesViewModel>()
    }

    @Test
    fun searchViewModelResolvesOverTheRealPlatforms() {
        val app = platformGraph()
        app.modules(searchModule)
        app.koin.get<SearchViewModel>()
    }

    @Test
    fun channelViewModelTakesPlatformAndLoginAsParameters() {
        val app = platformGraph()
        app.modules(channelModule)
        app.koin.get<ChannelViewModel> { parametersOf(Platform.KICK, "streamer1") }
        app.koin.get<ChannelViewModel> { parametersOf(Platform.TWITCH, "streamer1") }
    }

    @Test
    fun accountsViewModelResolvesOverTheRealSessions() {
        val app = platformGraph()
        app.modules(accountsModule)
        app.koin.get<AccountsViewModel>()
    }

    @Test
    fun loginViewModelTakesThePlatformAsAParameter() {
        val app = platformGraph()
        app.modules(accountsModule)
        app.koin.get<LoginViewModel> { parametersOf(Platform.TWITCH) }
        app.koin.get<LoginViewModel> { parametersOf(Platform.KICK) }
    }

    @Test
    fun loginModuleBindsTheAndroidCookieJar() {
        val koin = koinApplication { modules(loginModule) }.koin
        assertTrue(koin.get<CookieJar>() is AndroidCookieJar)
        assertSame(koin.get<CookieJar>(), koin.get<CookieJar>())
    }

    @Test
    fun categoryStreamsViewModelTakesPlatformAndIdAsParameters() {
        val app = platformGraph()
        app.modules(browseModule)
        app.koin.get<CategoryStreamsViewModel> { parametersOf(Platform.KICK, "42") }
    }

    @Test
    fun accountsStateResolvesOverTheRealSessions() {
        val koin = platformGraph().koin
        assertSame(koin.get<SessionRepository>(), koin.get<SessionRepository>())
        assertEquals(
            Platform.entries.toSet(),
            koin
                .get<AccountsState>()
                .states.value.keys,
        )
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

    @Test
    fun appScopeSwallowsAndLogsOnlyTheClassName() {
        val logged = mutableListOf<Triple<Severity, String, Throwable?>>()
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    logged += Triple(severity, message, throwable)
                }
            },
        )
        try {
            val scope = platformGraph().koin.get<CoroutineScope>(named("appScope"))
            runBlocking { scope.launch { error("secret-token-value") }.join() }

            assertTrue(scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
            val entry = logged.single()
            assertTrue("IllegalStateException" in entry.second)
            assertFalse("secret-token-value" in entry.second)
            assertNull(entry.third)
        } finally {
            Logger.setLogWriters(co.touchlab.kermit.platformLogWriter())
        }
    }
}

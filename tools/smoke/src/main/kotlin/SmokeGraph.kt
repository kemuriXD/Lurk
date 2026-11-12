package io.github.kemurixd.lurk.smoke

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.auth.authModule
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.SecureStore
import io.github.kemurixd.lurk.core.storage.createJvmDriver
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.github.kemurixd.lurk.platform.kick.kickModule
import io.github.kemurixd.lurk.platform.twitch.twitchModule
import io.github.kemurixd.lurk.playback.playbackModule
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

/** Keeps tokens in memory only. The smoke run never touches the device's secure storage. */
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

private object SmokeDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Default
}

/**
 * The app's real network, config, auth, playback and platform modules, minus the Android-only storage. Config is the
 * copy bundled at build time. Closing it cancels the app scope and closes the shared [HttpClient].
 */
internal class SmokeGraph : AutoCloseable {
    val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    Logger.withTag("AppScope").e { "Background task failed: ${e::class.simpleName}" }
                },
        )
    private val app: KoinApplication =
        koinApplication {
            modules(
                networkModule,
                configModule,
                playbackModule,
                authModule,
                twitchModule,
                kickModule,
                module {
                    single<DispatcherProvider> { SmokeDispatchers }
                    single(named("appScope")) { scope }
                    single<SecureStore> { MemorySecureStore() }
                    single { CacheStore(LurkDatabase(createJvmDriver()), get()) }
                    single<Clock> { Clock.System }
                },
            )
        }
    val koin: Koin get() = app.koin

    override fun close() {
        scope.cancel()
        koin.get<HttpClient>().close()
        app.close()
    }
}

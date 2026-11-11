package io.github.kemurixd.lurk.core.config

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertSame

class ConfigModuleTest {
    private object UnconfinedDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Test
    fun providesRepositorySingleton() {
        val deps =
            module {
                single<HttpClient> { createHttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }) }
                single { CacheStore(LurkDatabase(inMemoryDriver()), UnconfinedDispatchers) }
            }
        val koin = koinApplication { modules(deps, configModule) }.koin
        assertSame(koin.get<RemoteConfigRepository>(), koin.get<RemoteConfigRepository>())
        koin.get<HttpClient>().close()
    }
}

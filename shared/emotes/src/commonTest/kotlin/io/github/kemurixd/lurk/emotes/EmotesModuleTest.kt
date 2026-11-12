package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.core.network.WebSocketConnection
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import io.github.kemurixd.lurk.emotes.seventv.CosmeticsRegistry
import io.github.kemurixd.lurk.emotes.seventv.SevenTvEventClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.time.Clock

@OptIn(KoinExperimentalAPI::class, ExperimentalCoroutinesApi::class)
class EmotesModuleTest {
    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    private class OneShotConnector : WebSocketConnector {
        val inbound = Channel<String>(Channel.UNLIMITED)

        override suspend fun connect(
            url: String,
            headers: Map<String, String>,
        ): WebSocketConnection =
            object : WebSocketConnection {
                override val incoming: Flow<String> = inbound.consumeAsFlow()

                override suspend fun send(text: String) = Unit

                override suspend fun close() = Unit
            }
    }

    @Test
    fun emotesModuleIsCompleteGivenCoreBindings() {
        emotesModule.verify(
            extraTypes =
                listOf(
                    HttpClient::class,
                    CacheStore::class,
                    RemoteConfigRepository::class,
                    WebSocketConnector::class,
                    DispatcherProvider::class,
                    Clock::class,
                    StateFlow::class,
                    NativeEmoteSource::class,
                    Backoff::class,
                    EmoteRegistry::class,
                    CosmeticsRegistry::class,
                ),
        )
    }

    private fun TestScope.coreBindings(connector: WebSocketConnector = OneShotConnector()) =
        module {
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            single<DispatcherProvider> { dispatchers }
            single<HttpClient> { createHttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) }
            single<WebSocketConnector> { connector }
            single { CacheStore(LurkDatabase(inMemoryDriver()), dispatchers) }
            single { RemoteConfigRepository(get(), get(), Clock.System) }
        }

    private fun emote(
        id: String,
        name: String,
        provider: EmoteProvider,
    ) = Emote(id, name, provider, EmoteScope.CHANNEL, listOf(EmoteImage(1, "https://example.test/$id.png")))

    @Test
    fun loaderPublishesIntoTheRegistryItWasGiven() =
        runTest {
            val app = koinApplication { modules(coreBindings(), emotesModule) }
            val mine = EmoteRegistry()
            val other = EmoteRegistry()
            val native = NativeEmoteSource { listOf(emote("n1", "nativeWave", EmoteProvider.TWITCH)) }
            val loader = app.koin.get<EmoteLoader> { parametersOf(mine, native) }
            loader.load(EmoteChannel(Platform.TWITCH, "1000500"), emptySet(), backgroundScope)
            assertNotNull(mine.snapshot.value.find("nativeWave"))
            assertNull(other.snapshot.value.find("nativeWave"))
        }

    @Test
    fun loaderWorksWithoutANativeSource() =
        runTest {
            val app = koinApplication { modules(coreBindings(), emotesModule) }
            val loader = app.koin.get<EmoteLoader> { parametersOf(EmoteRegistry()) }
            val results = loader.load(EmoteChannel(Platform.TWITCH, "1000500"), emptySet(), backgroundScope)
            assertEquals(emptyList(), results)
        }

    @Test
    fun loaderAndEventClientShareTheRegistryTheyAreGiven() =
        runTest {
            val connector = OneShotConnector()
            val app = koinApplication { modules(coreBindings(connector), emotesModule) }
            val shared = EmoteRegistry()
            val cosmetics = CosmeticsRegistry()
            shared.addEmote(
                EmoteProvider.SEVENTV,
                EmoteScope.CHANNEL,
                emote("01AA0000000000000000000E02", "oldEmote", EmoteProvider.SEVENTV),
            )
            val native = NativeEmoteSource { listOf(emote("n1", "nativeWave", EmoteProvider.TWITCH)) }
            val loader = app.koin.get<EmoteLoader> { parametersOf(shared, native) }
            val client = app.koin.get<SevenTvEventClient> { parametersOf(shared, cosmetics) }

            loader.load(EmoteChannel(Platform.TWITCH, "1000500"), emptySet(), backgroundScope)
            assertNotNull(shared.snapshot.value.find("nativeWave"))

            backgroundScope.launch {
                client.run(Platform.TWITCH, "1000500", MutableStateFlow("01AA0000000000000000000011"))
            }
            connector.inbound.trySend("""{"op":1,"d":{"session_id":"s1","heartbeat_interval":25000}}""")
            connector.inbound.trySend(fixture("seventv/eventapi/dispatch-emote-set-update.json"))
            runCurrent()
            assertNotNull(shared.snapshot.value.find("pepeLaugh"))
            assertNull(shared.snapshot.value.find("oldEmote"))
        }

    @Test
    fun registriesAreFreshForEveryRequest() {
        val app = koinApplication { modules(emotesModule) }
        assertNotSame(app.koin.get<EmoteRegistry>(), app.koin.get<EmoteRegistry>())
        assertNotSame(app.koin.get<CosmeticsRegistry>(), app.koin.get<CosmeticsRegistry>())
    }
}

package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.DefaultDispatchers
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.playback.ads.AdDetector
import io.github.kemurixd.lurk.playback.proxy.ProxyChain
import io.github.kemurixd.lurk.playback.proxy.ProxyHealth
import io.github.kemurixd.lurk.playback.proxy.ProxyTester
import io.github.kemurixd.lurk.playback.proxy.RouteClients
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.TimeSource

@OptIn(KoinExperimentalAPI::class)
class PlaybackModuleTest {
    private val coreBindings =
        module {
            single<DispatcherProvider> { DefaultDispatchers }
            single<HttpClient> { createHttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) }
        }

    @Test
    fun playbackModuleIsCompleteGivenCoreBindings() {
        playbackModule.verify(
            extraTypes =
                listOf(
                    HttpClient::class,
                    DispatcherProvider::class,
                    RemoteConfigRepository::class,
                    TimeSource.WithComparableMarks::class,
                    TimeSource::class,
                    List::class,
                    Function0::class,
                    PlaybackSource::class,
                ),
        )
    }

    @Test
    fun healthChainAndRoutesAreShared() {
        val app = koinApplication { modules(coreBindings, playbackModule) }
        assertSame(app.koin.get<ProxyHealth>(), app.koin.get<ProxyHealth>())
        assertSame(app.koin.get<ProxyChain>(), app.koin.get<ProxyChain>())
        assertSame(app.koin.get<RouteClients>(), app.koin.get<RouteClients>())
        assertSame(app.koin.get<AdDetector>(), app.koin.get<AdDetector>())
    }

    @Test
    fun resolverWithNoSourcesReturnsNotFound() =
        runTest {
            val app = koinApplication { modules(coreBindings, playbackModule) }
            val resolver = app.koin.get<PlaylistResolver>()
            assertEquals(
                Outcome.Failure(LurkError.NotFound),
                resolver.resolve(TWITCH, PlaybackPrefs(), backgroundScope),
            )
        }

    @Test
    fun resolverUsesTheSourcesTheAppBinds() =
        runTest {
            val source = FakePlaybackSource(Platform.KICK)
            source.next = { Outcome.Failure(LurkError.IntegrityRequired) }
            val app =
                koinApplication { modules(coreBindings, module { single<PlaybackSource> { source } }, playbackModule) }
            val result = app.koin.get<PlaylistResolver>().resolve(KICK, PlaybackPrefs(), backgroundScope)
            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), result)
            assertEquals(1, source.calls)
        }

    @Test
    fun testerIsBuiltPerSource() {
        val app = koinApplication { modules(coreBindings, playbackModule) }
        val a = app.koin.get<ProxyTester> { parametersOf(FakePlaybackSource() as PlaybackSource) }
        val b = app.koin.get<ProxyTester> { parametersOf(FakePlaybackSource() as PlaybackSource) }
        assertNotSame(a, b)
    }
}

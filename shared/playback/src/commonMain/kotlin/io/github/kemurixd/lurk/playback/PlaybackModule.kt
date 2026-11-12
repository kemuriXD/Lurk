package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.playback.ads.AdDetector
import io.github.kemurixd.lurk.playback.ads.AdMarkers
import io.github.kemurixd.lurk.playback.proxy.DefaultRouteClients
import io.github.kemurixd.lurk.playback.proxy.ProxyChain
import io.github.kemurixd.lurk.playback.proxy.ProxyHealth
import io.github.kemurixd.lurk.playback.proxy.ProxyTester
import io.github.kemurixd.lurk.playback.proxy.RouteClients
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.TimeSource

/**
 * Binds the playback services. Needs `HttpClient`, `DispatcherProvider` and [RemoteConfigRepository] from the core
 * modules. The direct `HttpClient` is shared with the other modules, and [RouteClients] never closes it.
 *
 * [PlaylistResolver] collects every bound [PlaybackSource] with `getAll()`. None is bound until the platform modules
 * add theirs, and until then every resolve returns `LurkError.NotFound`.
 *
 * [ProxyTester] is a factory: `parametersOf(source)`. Calling `get<ProxyTester>()` without it throws at runtime, which
 * Koin's `verify()` cannot detect.
 *
 * The ad markers are read from [RemoteConfigRepository] on every use, so a patched `remote.json` applies to the next
 * stream.
 */
val playbackModule: Module =
    module {
        single { ProxyHealth(TimeSource.Monotonic) }
        single { ProxyChain(get(), TimeSource.Monotonic) }
        single<RouteClients> { DefaultRouteClients(direct = get()) }
        single { AdDetector { AdMarkers.from(get<RemoteConfigRepository>().config.value.twitch) } }
        single {
            PlaylistResolver(
                sources = getAll(),
                routes = get(),
                chain = get(),
                markers = { AdMarkers.from(get<RemoteConfigRepository>().config.value.twitch) },
                dispatchers = get(),
                timeSource = TimeSource.Monotonic,
            )
        }
        factory { params ->
            ProxyTester(
                source = params.get(),
                routes = get(),
                detector = get(),
                timeSource = TimeSource.Monotonic,
            )
        }
    }

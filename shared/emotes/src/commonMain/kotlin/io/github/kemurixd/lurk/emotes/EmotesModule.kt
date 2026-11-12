package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.emotes.seventv.CosmeticsRegistry
import io.github.kemurixd.lurk.emotes.seventv.SevenTvEventClient
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Binds the emote services. Needs `HttpClient`, `WebSocketConnector`, `DispatcherProvider`, `CacheStore` and
 * [RemoteConfigRepository] from the core modules.
 *
 * One [EmoteRegistry] and one [CosmeticsRegistry] belong to each open channel, and the loader and the 7TV event
 * client of that channel must write to the same pair. The two factories therefore take them as parameters:
 *
 * - [EmoteLoader]: `parametersOf(registry)` or `parametersOf(registry, nativeSource)`; the native source is optional.
 * - [SevenTvEventClient]: `parametersOf(registry, cosmetics)`.
 *
 * The caller creates the registries (`get<EmoteRegistry>()` returns a new one each time) and passes the same
 * instances to both factories.
 */
val emotesModule: Module =
    module {
        single { EmoteApi(get()) }
        factory { EmoteRegistry() }
        factory { CosmeticsRegistry() }
        factory { params ->
            EmoteLoader(
                api = get(),
                cache = get(),
                registry = params.get(),
                config = get<RemoteConfigRepository>().config,
                clock = Clock.System,
                native = params.getOrNull(),
            )
        }
        factory { params ->
            SevenTvEventClient(
                connector = get(),
                api = get(),
                registry = params.get(),
                cosmetics = params.get(),
                dispatchers = get(),
            )
        }
    }

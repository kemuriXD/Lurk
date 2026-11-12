package io.github.kemurixd.lurk.core.config

import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/** Binds [RemoteConfigRepository]. Needs an `HttpClient` and a `CacheStore` from the network and storage modules. */
val configModule: Module =
    module {
        single { RemoteConfigRepository(get(), get(), Clock.System) }
    }

package io.github.kemurixd.lurk.auth

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Binds the [SessionRepository]. Needs the platform `SecureStore`, every [ProfileFetcher] (one per platform, bound
 * by the platform modules) and a `CoroutineScope` named `appScope` that lives as long as the app.
 */
val authModule: Module =
    module {
        single { SessionRepository(get(), getAll(), get(named("appScope"))) }
    }

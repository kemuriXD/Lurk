package io.github.kemurixd.lurk.feature.common

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds [AccountsState] and the one [LastLiveLoader] every screen shares. Needs the `SessionRepository` from the auth
 * module, the app's `List<StreamingPlatform>`, the `DispatcherProvider` and the `Clock`.
 */
val featureCommonModule: Module =
    module {
        single { AccountsState(get()) }
        single { LastLiveLoader(get(), get(), get()) }
    }

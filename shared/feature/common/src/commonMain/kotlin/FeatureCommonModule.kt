package io.github.kemurixd.lurk.feature.common

import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds [AccountsState]. Needs the `SessionRepository` from the auth module. */
val featureCommonModule: Module =
    module {
        single { AccountsState(get()) }
    }

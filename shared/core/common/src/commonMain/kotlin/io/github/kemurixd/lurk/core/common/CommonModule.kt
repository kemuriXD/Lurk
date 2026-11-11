package io.github.kemurixd.lurk.core.common

import org.koin.dsl.module

val commonModule =
    module {
        single<DispatcherProvider> { DefaultDispatchers }
    }

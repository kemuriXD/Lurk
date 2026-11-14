package io.github.kemurixd.lurk.core.common

import org.koin.dsl.module
import kotlin.time.Clock

/** The dispatchers and the clock everything else takes from Koin. */
val commonModule =
    module {
        single<DispatcherProvider> { DefaultDispatchers }
        single<Clock> { Clock.System }
    }

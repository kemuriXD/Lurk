package io.github.kemurixd.lurk.core.common

import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.time.Clock

class CommonModuleTest {
    @Test
    fun bindsDefaultDispatchers() {
        val koin = koinApplication { modules(commonModule) }.koin
        assertSame(DefaultDispatchers, koin.get<DispatcherProvider>())
    }

    @Test
    fun bindsTheSystemClock() {
        val koin = koinApplication { modules(commonModule) }.koin
        assertSame(Clock.System, koin.get<Clock>())
    }
}

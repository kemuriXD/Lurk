package io.github.kemurixd.lurk.core.common

import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame

class CommonModuleTest {
    @Test
    fun bindsDefaultDispatchers() {
        val koin = koinApplication { modules(commonModule) }.koin
        assertSame(DefaultDispatchers, koin.get<DispatcherProvider>())
    }
}

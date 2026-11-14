package io.github.kemurixd.lurk.feature.testing

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Every dispatcher is [dispatcher], usually the test's `StandardTestDispatcher`, for virtual time. */
class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

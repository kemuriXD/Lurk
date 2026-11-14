package io.github.kemurixd.lurk.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has a validated network right now. The platform implements it; shared code only reads it. */
interface NetworkStatus {
    val isOnline: StateFlow<Boolean>
}

/** For platforms and tests with no connectivity source: it never blocks a request. */
object AlwaysOnline : NetworkStatus {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

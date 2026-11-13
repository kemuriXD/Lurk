package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The sign-in state of every platform, read from [SessionRepository]. ViewModels use this, never storage. */
class AccountsState(
    sessions: SessionRepository,
) {
    val states: StateFlow<Map<Platform, AccountState>> = CombinedStates(sessions)

    /** True when the platform has a stored session. Expired counts: the token is kept, and lists fall back. */
    fun signedIn(platform: Platform): Boolean = states.value.getValue(platform) != AccountState.SignedOut
}

/**
 * Combines the per-platform state flows without a scope of its own: [value] reads them directly and each collector
 * combines them while it collects. Nothing runs when nobody collects.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class CombinedStates(
    sessions: SessionRepository,
) : StateFlow<Map<Platform, AccountState>> {
    private val flows = Platform.entries.map { sessions.state(it) }

    override val value: Map<Platform, AccountState>
        get() = Platform.entries.zip(flows) { platform, flow -> platform to flow.value }.toMap()

    override val replayCache: List<Map<Platform, AccountState>>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<Map<Platform, AccountState>>): Nothing {
        combine(flows) { states -> Platform.entries.zip(states).toMap() }
            .distinctUntilChanged()
            .collect(collector)
        awaitCancellation()
    }
}

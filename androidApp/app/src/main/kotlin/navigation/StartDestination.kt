package io.github.kemurixd.lurk.navigation

import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.model.Platform

/** Following when any platform holds a session (an expired one counts), otherwise Browse (spec §2.1). */
fun startDestination(states: Map<Platform, AccountState>): TopLevel =
    if (states.values.any { it != AccountState.SignedOut }) TopLevel.FOLLOWING else TopLevel.BROWSE

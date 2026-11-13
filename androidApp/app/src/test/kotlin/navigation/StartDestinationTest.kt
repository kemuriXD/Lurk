package io.github.kemurixd.lurk.navigation

import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.testAccount
import kotlin.test.Test
import kotlin.test.assertEquals

class StartDestinationTest {
    @Test
    fun startsOnFollowingWhenAnySignedIn() {
        val states =
            mapOf(
                Platform.TWITCH to AccountState.SignedOut,
                Platform.KICK to AccountState.SignedIn(testAccount(Platform.KICK)),
            )

        assertEquals(TopLevel.FOLLOWING, startDestination(states))
    }

    @Test
    fun startsOnFollowingWhenExpired() {
        val states =
            mapOf(
                Platform.TWITCH to AccountState.Expired(testAccount(Platform.TWITCH)),
                Platform.KICK to AccountState.SignedOut,
            )

        assertEquals(TopLevel.FOLLOWING, startDestination(states))
    }

    @Test
    fun startsOnBrowseWhenSignedOut() {
        val states = Platform.entries.associateWith { AccountState.SignedOut }

        assertEquals(TopLevel.BROWSE, startDestination(states))
    }
}

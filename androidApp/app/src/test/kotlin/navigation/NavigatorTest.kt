package io.github.kemurixd.lurk.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.core.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorTest {
    private val channel = ChannelRoute(Platform.TWITCH, "streamer1")

    @Test
    fun selectingTheCurrentTabReturnsToItsRoot() {
        val stack = mutableListOf<NavKey>(SearchRoute, channel)

        Navigator(stack).select(TopLevel.SEARCH)

        assertEquals(listOf<NavKey>(SearchRoute), stack)
    }

    @Test
    fun backNeverEmptiesTheStack() {
        val stack = mutableListOf<NavKey>(BrowseRoute)

        Navigator(stack).back()

        assertEquals(listOf<NavKey>(BrowseRoute), stack)
    }

    @Test
    fun settingsIsNotPushedTwice() {
        val stack = mutableListOf<NavKey>(FollowingRoute)
        val navigator = Navigator(stack)

        navigator.openSettings()
        navigator.openSettings()

        assertEquals(listOf(FollowingRoute, SettingsRoute), stack)
    }
}

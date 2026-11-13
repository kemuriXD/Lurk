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

        Navigator(stack, start = TopLevel.SEARCH).select(TopLevel.SEARCH)

        assertEquals(listOf<NavKey>(SearchRoute), stack)
    }

    @Test
    fun backNeverEmptiesTheStack() {
        val stack = mutableListOf<NavKey>(BrowseRoute)

        Navigator(stack, start = TopLevel.BROWSE).back()

        assertEquals(listOf<NavKey>(BrowseRoute), stack)
    }

    @Test
    fun settingsIsNotPushedTwice() {
        val stack = mutableListOf<NavKey>(FollowingRoute)
        val navigator = Navigator(stack, start = TopLevel.FOLLOWING)

        navigator.openSettings()
        navigator.openSettings()

        assertEquals(listOf(FollowingRoute, SettingsRoute), stack)
    }

    @Test
    fun anotherTabSitsAboveTheStartTab() {
        val stack = mutableListOf<NavKey>(FollowingRoute, channel)
        val navigator = Navigator(stack, start = TopLevel.FOLLOWING)

        navigator.select(TopLevel.SEARCH)
        assertEquals(listOf(FollowingRoute, SearchRoute), stack)

        navigator.back()
        assertEquals(listOf<NavKey>(FollowingRoute), stack)
    }

    @Test
    fun theStartTabIsAloneOnTheStack() {
        val stack = mutableListOf<NavKey>(FollowingRoute, SearchRoute, channel)

        Navigator(stack, start = TopLevel.FOLLOWING).select(TopLevel.FOLLOWING)

        assertEquals(listOf<NavKey>(FollowingRoute), stack)
    }
}

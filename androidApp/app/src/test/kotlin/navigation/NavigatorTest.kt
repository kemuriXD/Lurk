package io.github.kemurixd.lurk.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.core.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {
    private val channel = ChannelRoute(Platform.TWITCH, "streamer1")
    private val category = CategoryRoute(Platform.TWITCH, "42", "Retro Racers")
    private val player = PlayerRoute(Platform.TWITCH, "streamer1", "Test stream", thumbnailUrl = null)

    @Test
    fun everyTabStartsAtItsRoot() {
        val navigator = Navigator(start = TopLevel.BROWSE)

        assertEquals(TopLevel.BROWSE, navigator.current)
        TopLevel.entries.forEach { assertEquals(listOf(it.route), navigator.stack(it)) }
    }

    @Test
    fun eachTabKeepsItsOwnStack() {
        val navigator = Navigator(start = TopLevel.BROWSE)
        navigator.push(category)
        navigator.push(channel)

        navigator.select(TopLevel.SEARCH)
        assertEquals(TopLevel.SEARCH, navigator.current)
        assertEquals(listOf<NavKey>(SearchRoute), navigator.currentStack)

        navigator.select(TopLevel.BROWSE)
        assertEquals(listOf(BrowseRoute, category, channel), navigator.currentStack)
    }

    @Test
    fun pushesGoOntoTheCurrentTabsStackOnly() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)
        navigator.select(TopLevel.SEARCH)

        listOf(category, channel, player, SettingsRoute, LoginRoute(Platform.KICK)).forEach(navigator::push)

        assertEquals(
            listOf(SearchRoute, category, channel, player, SettingsRoute, LoginRoute(Platform.KICK)),
            navigator.stack(TopLevel.SEARCH),
        )
        assertEquals(listOf<NavKey>(FollowingRoute), navigator.stack(TopLevel.FOLLOWING))
        assertEquals(listOf<NavKey>(BrowseRoute), navigator.stack(TopLevel.BROWSE))
    }

    @Test
    fun backPopsTheCurrentTabsStack() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)
        navigator.select(TopLevel.SEARCH)
        navigator.push(category)
        navigator.push(channel)

        navigator.back()

        assertEquals(TopLevel.SEARCH, navigator.current)
        assertEquals(listOf(SearchRoute, category), navigator.currentStack)
    }

    @Test
    fun backAtAnotherTabsRootShowsTheStartTabAsItWasLeft() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)
        navigator.push(channel)
        navigator.select(TopLevel.SEARCH)

        navigator.back()

        assertEquals(TopLevel.FOLLOWING, navigator.current)
        assertEquals(listOf(FollowingRoute, channel), navigator.currentStack)
        // The tab left behind keeps its root, ready for next time.
        assertEquals(listOf<NavKey>(SearchRoute), navigator.stack(TopLevel.SEARCH))
    }

    @Test
    fun backAtTheStartTabsRootIsLeftToTheSystem() {
        val navigator = Navigator(start = TopLevel.BROWSE)

        assertFalse(navigator.canGoBack)
        navigator.back()

        assertEquals(TopLevel.BROWSE, navigator.current)
        assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack)
    }

    @Test
    fun canGoBackOnAPushedPageAndOnAnotherTabsRoot() {
        val navigator = Navigator(start = TopLevel.BROWSE)
        navigator.push(channel)
        assertTrue(navigator.canGoBack)

        navigator.back()
        navigator.select(TopLevel.SEARCH)
        assertTrue(navigator.canGoBack)
    }

    @Test
    fun selectingTheCurrentTabReturnsToItsRoot() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)
        navigator.select(TopLevel.SEARCH)
        navigator.push(category)
        navigator.push(channel)

        navigator.select(TopLevel.SEARCH)

        assertEquals(listOf<NavKey>(SearchRoute), navigator.currentStack)
    }

    @Test
    fun settingsIsNotPushedTwice() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)

        navigator.openSettings()
        navigator.openSettings()

        assertEquals(listOf(FollowingRoute, SettingsRoute), navigator.currentStack)
    }

    @Test
    fun theSameChannelCanBeOpenInTwoTabs() {
        val navigator = Navigator(start = TopLevel.BROWSE)
        navigator.push(channel)
        navigator.select(TopLevel.SEARCH)
        navigator.push(channel)

        assertEquals(listOf(BrowseRoute, channel), navigator.stack(TopLevel.BROWSE))
        assertEquals(listOf(SearchRoute, channel), navigator.stack(TopLevel.SEARCH))
    }

    @Test
    fun aNewStartTabKeepsThePageInFrontAndResetsTheOtherTabs() {
        // Signed out: Browse is the start tab. The user visited Following and Search, then signs in from Settings.
        val navigator = Navigator(start = TopLevel.BROWSE)
        navigator.select(TopLevel.FOLLOWING)
        navigator.push(channel)
        navigator.select(TopLevel.SEARCH)
        navigator.push(category)
        navigator.select(TopLevel.BROWSE)
        navigator.openSettings()
        navigator.push(LoginRoute(Platform.TWITCH))

        navigator.changeStart(TopLevel.FOLLOWING)

        assertEquals(TopLevel.FOLLOWING, navigator.start)
        assertEquals(TopLevel.BROWSE, navigator.current)
        assertEquals(listOf(BrowseRoute, SettingsRoute, LoginRoute(Platform.TWITCH)), navigator.currentStack)
        assertEquals(listOf<NavKey>(FollowingRoute), navigator.stack(TopLevel.FOLLOWING))
        assertEquals(listOf<NavKey>(SearchRoute), navigator.stack(TopLevel.SEARCH))

        // Back now unwinds Browse, then lands on the new start tab, whose root is where the app exits.
        navigator.back()
        navigator.back()
        assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack)
        navigator.back()
        assertEquals(TopLevel.FOLLOWING, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun theSameStartTabAgainChangesNothing() {
        val navigator = Navigator(start = TopLevel.FOLLOWING)
        navigator.push(channel)
        navigator.select(TopLevel.SEARCH)
        navigator.push(category)

        navigator.changeStart(TopLevel.FOLLOWING)

        assertEquals(listOf(FollowingRoute, channel), navigator.stack(TopLevel.FOLLOWING))
        assertEquals(listOf(SearchRoute, category), navigator.stack(TopLevel.SEARCH))
        assertEquals(TopLevel.SEARCH, navigator.current)
    }
}

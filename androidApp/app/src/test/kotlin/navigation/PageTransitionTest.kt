package io.github.kemurixd.lurk.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import io.github.kemurixd.lurk.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTransitionTest {
    private val channel = ChannelRoute(Platform.TWITCH, "streamer1")
    private val category = CategoryRoute(Platform.KICK, "1", "Just Chatting")

    private fun direction(
        kind: NavigationKind,
        from: Any?,
        to: Any?,
    ) = pageTransition(kind, from, to).direction

    @Test
    fun pushingAPageSlidesItInFromTheRight() {
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, FollowingRoute, channel))
        assertEquals(
            SlideDirection.FORWARD,
            direction(NavigationKind.PUSH, channel, PlayerRoute(Platform.TWITCH, "streamer1", "t", null)),
        )
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, BrowseRoute, category))
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, SearchRoute, SettingsRoute))
    }

    @Test
    fun poppingAPageSlidesItOutToTheRight() {
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.POP, channel, FollowingRoute))
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.POP, SettingsRoute, BrowseRoute))
    }

    @Test
    fun aTabFurtherRightSlidesLeft() {
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, FollowingRoute, BrowseRoute))
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, BrowseRoute, SearchRoute))
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, FollowingRoute, SearchRoute))
    }

    @Test
    fun aTabFurtherLeftSlidesRight() {
        // A tab switch can read as a push even when it goes left.
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.PUSH, SearchRoute, FollowingRoute))
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.PUSH, SearchRoute, BrowseRoute))
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.POP, BrowseRoute, FollowingRoute))
    }

    @Test
    fun aTabSwitchGoesTheTabsWayWhateverTheStackDoes() {
        // Browse as the start tab, then Following above it: a "push" to a tab on the left.
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.PUSH, BrowseRoute, FollowingRoute))
        // Back from it: a "pop" to a tab on the right.
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.POP, FollowingRoute, BrowseRoute))
    }

    @Test
    fun everyKindSharesOneDurationAndEasing() {
        val specs =
            NavigationKind.entries.map { pageTransition(it, FollowingRoute, channel) }.map {
                it.durationMillis to
                    it.easing
            }
        assertEquals(1, specs.toSet().size)
        assertEquals(300, specs.first().first)
        // Tab switches use the same spec as pushes and pops.
        val tab = pageTransition(NavigationKind.PUSH, FollowingRoute, BrowseRoute)
        assertEquals(specs.first(), tab.durationMillis to tab.easing)
    }

    private fun scene(
        key: NavKey,
        tab: TopLevel = checkNotNull(TopLevel.of(key)),
        index: Int = 0,
    ): Scene<NavKey> {
        val entry = tabEntry(tab, index, key) { NavEntry(it) {} }
        return object : Scene<NavKey> {
            override val key: Any = entry.contentKey
            override val entries = listOf(entry)
            override val previousEntries = emptyList<NavEntry<NavKey>>()
            override val content: @Composable () -> Unit = {}
        }
    }

    @Test
    fun scenesFromTheNavDisplayStillGoTheTabsWay() {
        // What slide() receives: real entries, whose contentKey is not the NavKey.
        assertEquals(
            SlideDirection.BACKWARD,
            pageTransition(NavigationKind.PUSH, scene(SearchRoute), scene(FollowingRoute)).direction,
        )
        assertEquals(
            SlideDirection.FORWARD,
            pageTransition(NavigationKind.POP, scene(FollowingRoute), scene(BrowseRoute)).direction,
        )
        assertEquals(
            SlideDirection.FORWARD,
            pageTransition(NavigationKind.PUSH, scene(FollowingRoute), scene(channel, TopLevel.FOLLOWING, 1)).direction,
        )
    }

    @Test
    fun aTabSwitchFromAPushedPageStillGoesTheTabsWay() {
        // Browse > Channel in front, then Search, whose stack is at its root.
        assertEquals(
            SlideDirection.FORWARD,
            pageTransition(NavigationKind.PUSH, scene(channel, TopLevel.BROWSE, 1), scene(SearchRoute)).direction,
        )
        // And back to Browse, which still shows the channel.
        assertEquals(
            SlideDirection.BACKWARD,
            pageTransition(NavigationKind.PUSH, scene(SearchRoute), scene(channel, TopLevel.BROWSE, 1)).direction,
        )
    }

    @Test
    fun backFromAnotherTabsRootToTheStartTabGoesTheTabsWay() {
        // Start tab Browse showing a category; back from Following's root slides as a move right, not as a pop.
        assertEquals(
            SlideDirection.FORWARD,
            pageTransition(NavigationKind.POP, scene(FollowingRoute), scene(category, TopLevel.BROWSE, 1)).direction,
        )
        // Start tab Following showing a channel; back from Search's root is a move left.
        assertEquals(
            SlideDirection.BACKWARD,
            pageTransition(NavigationKind.POP, scene(SearchRoute), scene(channel, TopLevel.FOLLOWING, 1)).direction,
        )
    }

    @Test
    fun pushesAndPopsInsideOneTabStillSlideForwardAndBack() {
        assertEquals(
            SlideDirection.FORWARD,
            pageTransition(NavigationKind.PUSH, scene(category, TopLevel.SEARCH, 1), scene(channel, TopLevel.SEARCH, 2))
                .direction,
        )
        assertEquals(
            SlideDirection.BACKWARD,
            pageTransition(NavigationKind.POP, scene(channel, TopLevel.SEARCH, 1), scene(SearchRoute)).direction,
        )
    }

    @Test
    fun theSameRouteHasADifferentContentKeyInEachTabAndPlace() {
        val keys =
            listOf(
                tabEntry(TopLevel.BROWSE, 1, channel) { NavEntry(it) {} },
                tabEntry(TopLevel.SEARCH, 1, channel) { NavEntry(it) {} },
                tabEntry(TopLevel.SEARCH, 3, channel) { NavEntry(it) {} },
            ).map { it.contentKey }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun aTabEntryKeepsTheRoutesOwnMetadata() {
        val entry = tabEntry(TopLevel.BROWSE, 1, channel) { NavEntry(it, metadata = mapOf("own" to 1)) {} }

        assertEquals(1, entry.metadata["own"])
    }
}

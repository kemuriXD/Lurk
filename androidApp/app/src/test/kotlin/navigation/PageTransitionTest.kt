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
    fun theBackSwipeIsTheSamePopSlide() {
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.PREDICTIVE_POP, channel, FollowingRoute))
        // Even between tabs: the finger drags the current page to the right.
        assertEquals(SlideDirection.BACKWARD, direction(NavigationKind.PREDICTIVE_POP, FollowingRoute, SearchRoute))
    }

    @Test
    fun aTabFurtherRightSlidesLeft() {
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, FollowingRoute, BrowseRoute))
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, BrowseRoute, SearchRoute))
        assertEquals(SlideDirection.FORWARD, direction(NavigationKind.PUSH, FollowingRoute, SearchRoute))
    }

    @Test
    fun aTabFurtherLeftSlidesRight() {
        // Tab switches reset the stack, so one can read as a push even when it goes left.
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
        val all =
            NavigationKind.entries.map { kind ->
                pageTransition(kind, FollowingRoute, channel).let { it.durationMillis to it.easing }
            }
        assertEquals(1, all.toSet().size)
        assertEquals(300, all.first().first)
        // Tab switches use the same spec as pushes and pops.
        val tab = pageTransition(NavigationKind.PUSH, FollowingRoute, BrowseRoute)
        assertEquals(all.first(), tab.durationMillis to tab.easing)
    }

    private fun scene(key: NavKey): Scene<NavKey> {
        val entry = NavEntry(key, metadata = routeMetadata(key)) {}
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
            pageTransition(NavigationKind.PUSH, scene(FollowingRoute), scene(channel)).direction,
        )
    }
}

package io.github.kemurixd.lurk.navigation

import io.github.kemurixd.lurk.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun everyKindIsAPlainSlideOfAboutThreeHundredMilliseconds() {
        for (kind in NavigationKind.entries) {
            val transition = pageTransition(kind, FollowingRoute, channel)
            assertEquals(300, transition.durationMillis)
        }
    }

    @Test
    fun theBackSwipeFollowsTheFingerLinearly() {
        assertTrue(pageTransition(NavigationKind.PREDICTIVE_POP, channel, FollowingRoute).linear)
        assertTrue(!pageTransition(NavigationKind.PUSH, FollowingRoute, channel).linear)
        assertTrue(!pageTransition(NavigationKind.POP, channel, FollowingRoute).linear)
    }
}

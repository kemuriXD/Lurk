package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.time.LocalClock
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.following.FollowingSort
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

/** How the Offline section folds and unfolds: the rows fade, and an unfold near the bottom scrolls a row into view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineFoldMotionTest {
    @get:Rule val rule = createComposeRule()

    private var state by mutableStateOf(loaded(live = emptyList(), collapsed = false))
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-13T12:00:00Z")
        }

    private var viewport by mutableStateOf<Dp?>(null)
    private var density = 1f

    /** Cuts the screen so that its bottom edge is exactly the Offline header's bottom: no offline row is in reach. */
    private fun putTheHeaderOnTheBottomEdge() {
        val bottom = header().fetchSemanticsNode().boundsInRoot.bottom
        rule.runOnIdle { viewport = Dp(bottom / density) }
        rule.waitForIdle()
    }

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                LurkTheme(dynamicColor = false) {
                    density = LocalDensity.current.density
                    Box(viewport?.let { Modifier.height(it) } ?: Modifier.fillMaxSize()) {
                        FollowingScreen(
                            state = state,
                            onIntent = {},
                            onOpenPlayer = {},
                            onOpenChannel = {},
                            onSignIn = {},
                            onOpenBrowse = {},
                        )
                    }
                }
            }
        }
    }

    private fun header() = rule.onNode(isHeading() and hasText("Offline"))

    private fun headerTop() = header().fetchSemanticsNode().boundsInRoot.top

    /** The visible height of the first row: the animated container clips it, so the clipped bounds shrink with it. */
    private fun rowHeight() =
        rule
            .onAllNodesWithText("Sleeper1")
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.height
            ?.toInt()

    private fun fold(collapsed: Boolean) =
        rule.runOnIdle {
            state = state.copy(offlineCollapsed = collapsed)
            Snapshot.sendApplyNotifications()
        }

    /** The rows stay in the list while they shrink, and only then go. */
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h900dp")
    fun foldingShrinksTheRowsBeforeRemovingThem() {
        show()
        val full = rowHeight()!!
        rule.mainClock.autoAdvance = false
        fold(true)
        var smallest = full
        repeat(8) {
            rule.mainClock.advanceTimeByFrame()
            rowHeight()?.let { smallest = minOf(smallest, it) }
        }
        assertTrue("the row only got down to $smallest of $full px", smallest in 1 until full)
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Sleeper1").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h900dp")
    fun unfoldingGrowsTheRowsFromNothing() {
        state = state.copy(offlineCollapsed = true)
        show()
        rule.mainClock.autoAdvance = false
        fold(false)
        val heights = mutableListOf<Int>()
        repeat(40) {
            rule.mainClock.advanceTimeByFrame()
            rowHeight()?.let { heights += it }
        }
        assertTrue("heights $heights", heights.isNotEmpty() && heights.any { it < heights.last() })
    }

    /** Scrolled to the end, a fold glides the header down as the rows shrink; it never jumps in one frame. */
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h480dp")
    fun foldingAtTheEndOfTheListMovesTheHeaderInSmallSteps() {
        state = loaded(live = listOf(stream("streamer1"), stream("streamer2")), collapsed = false)
        show()
        rule.onNodeWithTag(FollowingTestTags.LIST).performScrollToNode(hasText("Sleeper3"))
        rule.waitForIdle()
        val rows =
            rule
                .onNodeWithText("Sleeper1")
                .fetchSemanticsNode()
                .boundsInRoot.height
                .toInt() * 3
        var last = headerTop()
        rule.mainClock.autoAdvance = false
        fold(true)
        var biggestStep = 0f
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            val top = headerTop()
            biggestStep = maxOf(biggestStep, kotlin.math.abs(top - last))
            last = top
        }
        assertTrue("the header jumped $biggestStep px of $rows in one frame", biggestStep < rows / 4f)
    }

    @Test
    fun foldingLeavesTheHeaderWhereItWas() {
        show()
        val before = headerTop()
        rule.runOnIdle { state = state.copy(offlineCollapsed = true) }
        rule.waitForIdle()
        assertEquals(before, headerTop(), 0.5f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h900dp")
    fun unfoldingWithARowAlreadyInViewDoesNotScroll() {
        state = state.copy(offlineCollapsed = true)
        show()
        val before = headerTop()
        rule.runOnIdle { state = state.copy(offlineCollapsed = false) }
        rule.waitForIdle()
        rule.onNodeWithText("Sleeper1").assertExists()
        assertEquals(before, headerTop(), 0.5f)
    }

    @Test
    fun unfoldingNearTheBottomScrollsExactlyAFullRowIntoView() {
        // On the test screen one live card leaves the Offline header just above the bottom edge.
        state = loaded(live = listOf(stream("streamer1")), collapsed = true)
        show()
        rule.waitForIdle()
        rule.runOnIdle { state = state.copy(offlineCollapsed = false) }
        rule.waitForIdle()
        val list = rule.onNodeWithTag(FollowingTestTags.LIST).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithText("Sleeper1").fetchSemanticsNode()
        val shown = row.boundsInRoot.height
        assertTrue("the row shows $shown of ${row.size.height} px", shown >= row.size.height - 1)
        assertTrue("the header stays in view, top ${headerTop()}", headerTop() >= list.top - 1)
        // The row ends on the list's real bottom edge, so the next row can't show in the padding band.
        val end = list.bottom
        assertEquals("row bottom ${row.boundsInRoot.bottom} against $end", end, row.boundsInRoot.bottom, 2f)
        // No sliver of the second row in the padding band under the first.
        val second = rule.onAllNodesWithText("Sleeper2").fetchSemanticsNodes().firstOrNull()
        assertTrue(
            "the second row peeks in: ${second?.boundsInRoot}",
            second == null || second.boundsInRoot.height < 1f,
        )
    }

    private fun loaded(
        live: List<Stream>,
        collapsed: Boolean,
    ): FollowingUiState {
        val offline = (1..3).map { channel("sleeper$it") }
        return FollowingUiState(
            filter = PlatformFilter.ALL,
            sort = FollowingSort.VIEWERS,
            live = live,
            offline = offline,
            slices =
                mapOf(
                    Platform.TWITCH to PlatformSlice.Loaded(Followed(live, offline)),
                    Platform.KICK to PlatformSlice.Loaded(Followed(emptyList(), emptyList())),
                ),
            refreshing = false,
            offlineCollapsed = collapsed,
        )
    }

    private fun stream(login: String) =
        Stream(
            channel = ChannelRef(Platform.TWITCH, login),
            channelId = "1000001",
            displayName = login.replaceFirstChar { it.uppercase() },
            title = "Speedrunning all night",
            category = null,
            viewers = 1_200,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )

    private fun channel(login: String) =
        Channel(
            ref = ChannelRef(Platform.TWITCH, login),
            id = "1000002",
            chatId = login,
            displayName = login.replaceFirstChar { it.uppercase() },
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = null,
            lastStream = null,
        )

    /** The first offline row is not composed at all, yet the reveal must still happen and finish. */
    @Test
    fun unfoldingWithTheHeaderOnTheBottomEdgeStillRevealsARow() {
        state = loaded(live = listOf(stream("streamer1")), collapsed = true)
        show()
        rule.waitForIdle()
        putTheHeaderOnTheBottomEdge()
        rule.onNodeWithText("Sleeper1").assertDoesNotExist()
        fold(false)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        val list = rule.onNodeWithTag(FollowingTestTags.LIST).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithText("Sleeper1").fetchSemanticsNode()
        val shown = row.boundsInRoot.height
        assertTrue("the row shows $shown of ${row.size.height} px", shown >= row.size.height - 1)
        assertEquals(list.bottom, row.boundsInRoot.bottom, 2f)
    }
}

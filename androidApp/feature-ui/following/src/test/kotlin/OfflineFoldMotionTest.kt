package io.github.kemurixd.lurk.featureui.following

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

/** How the Offline section folds and unfolds: the rows fade, and an unfold near the bottom scrolls a row into view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineFoldMotionTest {
    @get:Rule val rule = createComposeRule()

    private var state by mutableStateOf(loaded(live = emptyList(), collapsed = false))
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-13T12:00:00Z")
        }

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                LurkTheme(dynamicColor = false) {
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

    private fun header() = rule.onNode(isHeading() and hasText("Offline"))

    private fun headerTop() = header().fetchSemanticsNode().boundsInRoot.top

    /** The pixels of [area] on screen right now. */
    private fun pixels(area: Rect): IntArray {
        val image = rule.onRoot().captureToImage()
        val out = IntArray(image.width * image.height)
        image.readPixels(out, 0, 0, image.width, image.height)
        val rows = area.top.toInt()..<area.bottom.toInt()
        val columns = area.left.toInt()..<area.right.toInt()
        return rows.flatMap { y -> columns.map { x -> out[y * image.width + x] } }.toIntArray()
    }

    /**
     * Rows that leave a lazy list are no longer composed, only drawn while they fade, so the fade can't be seen in the
     * semantics tree. Their pixels can: the frame the fold first shows, the rows' place still differs from what it
     * looks like once the fade is over.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h900dp")
    fun foldingFadesTheRowsOutInsteadOfCuttingThem() {
        show()
        val area = rule.onNodeWithText("Sleeper1").fetchSemanticsNode().boundsInRoot
        val before = pixels(area)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            state = state.copy(offlineCollapsed = true)
            Snapshot.sendApplyNotifications()
        }
        var frames = 0
        var now = before
        while (now.contentEquals(before) && frames < MAX_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            now = pixels(area)
            frames++
        }
        assertTrue("the fold never showed in $frames frames", !now.contentEquals(before))
        val firstFoldedFrame = now
        rule.mainClock.advanceTimeBy(2_000)
        val settled = pixels(area)
        rule.onNodeWithText("Sleeper1").assertDoesNotExist()
        val stillThere = firstFoldedFrame.indices.count { firstFoldedFrame[it] != settled[it] }
        assertTrue("only $stillThere px still show the row on the first folded frame", stillThere > MIN_FADING_PIXELS)
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
    fun unfoldingNearTheBottomScrollsAFullRowIntoView() {
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

    private companion object {
        const val MAX_FRAMES = 30
        const val MIN_FADING_PIXELS = 100
    }
}

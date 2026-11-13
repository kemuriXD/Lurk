package io.github.kemurixd.lurk.featureui.following

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first

/** The lazy key of the Offline header item. */
internal const val OFFLINE_HEADER_KEY = "offline_header"

/** Reveal a sliver, measure, then scroll the rest: two passes, one spare. */
private const val REVEAL_PASSES = 3

/** The most frames to wait for the first row to stop changing size. */
private const val MAX_SETTLE_FRAMES = 90

/**
 * How far to scroll, in pixels, after the Offline section unfolds so that its header and the first row are both fully
 * in view, never more than brings the header to the top edge. The header starts [headerTop] from the top of the list
 * and is [headerHeight] tall; [rowHeight] is the first row's height; [viewportEnd] is where the list's visible area
 * ends.
 */
internal fun offlineRevealScroll(
    headerTop: Int,
    headerHeight: Int,
    rowHeight: Int,
    viewportEnd: Int,
): Int {
    val missing = headerTop + headerHeight + rowHeight - viewportEnd
    // Never scroll so far that the header itself leaves the top edge.
    return missing.coerceIn(0, headerTop.coerceAtLeast(0))
}

/**
 * When the Offline section goes from folded to unfolded, scrolls the list (animated) just enough that the header and
 * the first row are fully in view. Nothing happens on the first composition, when the section starts unfolded, nor on
 * a fold, nor when the row is already in view. It waits until the rows have finished growing, so it measures them at
 * their real height.
 */
@Composable
internal fun RevealOfflineRowsOnUnfold(
    grid: LazyGridState,
    collapsed: Boolean,
    shown: MutableTransitionState<Boolean>,
    firstRowKey: Any?,
) {
    var wasCollapsed by remember { mutableStateOf(collapsed) }
    LaunchedEffect(collapsed) {
        val unfolded = wasCollapsed && !collapsed
        wasCollapsed = collapsed
        if (!unfolded || firstRowKey == null) return@LaunchedEffect
        snapshotFlow { shown.currentState && shown.isIdle }.first { it }
        revealOfflineRows(grid, firstRowKey)
    }
}

/**
 * Scrolls [grid] just far enough to show the Offline header and the first row, [firstRowKey], in full. When that row
 * isn't composed yet, it first scrolls a single pixel so the row exists, measures it, and then scrolls the rest, so it
 * never goes by a guess. It does nothing while the user is scrolling, and a scroll the user starts later takes over.
 */
internal suspend fun revealOfflineRows(
    grid: LazyGridState,
    firstRowKey: Any,
) {
    awaitAtRest(grid, firstRowKey)
    repeat(REVEAL_PASSES) { if (!revealStep(grid, firstRowKey)) return }
}

/** One pass of [revealOfflineRows]. True when the list should be measured again, because it moved a sliver only. */
private suspend fun revealStep(
    grid: LazyGridState,
    firstRowKey: Any,
): Boolean {
    val info = grid.layoutInfo
    val header = info.visibleItemsInfo.firstOrNull { it.key == OFFLINE_HEADER_KEY }
    val row = info.visibleItemsInfo.firstOrNull { it.key == firstRowKey }
    return when {
        grid.isScrollInProgress || header == null -> {
            false
        }

        row == null -> {
            (grid.scrollBy(1f) != 0f).also { moved -> if (moved) withFrameNanos { } }
        }

        else -> {
            val delta =
                offlineRevealScroll(
                    headerTop = header.offset.y,
                    headerHeight = header.size.height,
                    rowHeight = row.size.height,
                    viewportEnd = info.viewportEndOffset - info.afterContentPadding,
                )
            if (delta > 0) grid.animateScrollBy(delta.toFloat())
            false
        }
    }
}

/** Waits until the first row, if it is on screen, has kept the same height for two frames in a row. */
private suspend fun awaitAtRest(
    grid: LazyGridState,
    firstRowKey: Any,
) {
    fun height() =
        grid.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == firstRowKey }
            ?.size
            ?.height
    var last = height()
    repeat(MAX_SETTLE_FRAMES) {
        withFrameNanos { }
        val now = height()
        if (now == null || now == last) return
        last = now
    }
}

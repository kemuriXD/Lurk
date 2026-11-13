package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/** The lazy key of the Offline header item. */
internal const val OFFLINE_HEADER_KEY = "offline_header"

/** Reveal a sliver, measure, then scroll the rest: two passes, one spare. */
private const val REVEAL_PASSES = 3

/** The most frames to wait for the first row to stop changing size. */
private const val MAX_SETTLE_FRAMES = 90

/**
 * How far to scroll, in pixels (positive: further down the list), after the Offline section unfolds so that the list's
 * bottom edge sits exactly on a row boundary: the header and the first row are fully in view, and no row is cut or
 * peeks in at the edge. The header starts [headerTop] from the top of the list and is [headerHeight] tall; [rowHeight]
 * is the height of each of the [rowCount] rows; [viewportEnd] is the list's real bottom edge, bottom padding
 * included, because rows are drawn in the padding too and a row that starts there would show as a sliver.
 *
 * - If every row fits above the edge, nothing peeks and the result is 0.
 * - Otherwise the edge is brought onto the bottom of the last row that fits whole, or the first row if even that does
 *   not fit. A partly visible next row is therefore scrolled back out of view rather than forward into it.
 * - Scrolling forward never takes the header past the top edge.
 */
internal fun offlineRevealScroll(
    headerTop: Int,
    headerHeight: Int,
    rowHeight: Int,
    rowCount: Int,
    viewportEnd: Int,
): Int {
    val rowsTop = headerTop + headerHeight
    if (rowHeight <= 0 || viewportEnd >= rowsTop + rowCount * rowHeight) return 0
    val fitting = Math.floorDiv(viewportEnd - rowsTop, rowHeight).coerceIn(1, rowCount.coerceAtLeast(1))
    val delta = rowsTop + fitting * rowHeight - viewportEnd
    return delta.coerceIn(-rowHeight, headerTop.coerceAtLeast(0))
}

/** How long the rows take to grow in or shrink out. The fold's own timer uses it too, so it never waits on a row. */
internal const val OFFLINE_FOLD_MILLIS = 300

/** A little past the animation, so the rows have finished before they are dropped or measured. */
private const val OFFLINE_FOLD_MARGIN_MILLIS = 50L

/**
 * The fold state of the Offline section: whether the rows are in the lazy list at all, and whether the section has
 * settled unfolded. Both follow the fold state and a timer, never a row's own animation, because a row that is not on
 * screen is not composed and would never finish.
 */
internal class OfflineFold(
    expanded: Boolean,
) {
    var rowsInList by mutableStateOf(expanded)

    /**
     * Whether the section has come to rest unfolded. A row that is composed while this is true appears at full size;
     * one composed while the section is still unfolding grows in.
     */
    var settledExpanded by mutableStateOf(expanded)
}

/**
 * Keeps the [OfflineFold] for a list whose Offline section is [collapsed]. A fold keeps the rows in the list until
 * their shrinking is over, then drops them whether or not any was on screen. An unfold puts them in at once; when it
 * is over, it scrolls the list (animated) just enough that the header and the first row, [firstRowKey], are fully in
 * view. Nothing happens on the first composition, nor when the row is already in view.
 */
@Composable
internal fun rememberOfflineFold(
    grid: LazyGridState,
    collapsed: Boolean,
    firstRowKey: Any?,
    rowCount: Int,
): OfflineFold {
    val fold = remember { OfflineFold(expanded = !collapsed) }
    var wasCollapsed by remember { mutableStateOf(collapsed) }
    LaunchedEffect(collapsed) {
        val unfolded = wasCollapsed && !collapsed
        val folded = !wasCollapsed && collapsed
        wasCollapsed = collapsed
        if (unfolded) fold.rowsInList = true
        if (folded) fold.settledExpanded = false
        if (!unfolded && !folded) return@LaunchedEffect
        delay(OFFLINE_FOLD_MILLIS + OFFLINE_FOLD_MARGIN_MILLIS)
        fold.settledExpanded = !collapsed
        if (folded) {
            fold.rowsInList = false
        } else if (firstRowKey != null) {
            revealOfflineRows(grid, firstRowKey, rowCount)
        }
    }
    return fold
}

/**
 * Scrolls [grid] so that its bottom edge sits on a row boundary with the Offline header and the first row,
 * [firstRowKey], in full (see [offlineRevealScroll]). When that row
 * isn't composed yet, it first scrolls a single pixel so the row exists, measures it, and then scrolls the rest, so it
 * never goes by a guess. It does nothing while the user is scrolling, and a scroll the user starts later takes over.
 */
internal suspend fun revealOfflineRows(
    grid: LazyGridState,
    firstRowKey: Any,
    rowCount: Int,
) {
    awaitAtRest(grid, firstRowKey)
    repeat(REVEAL_PASSES) { if (!revealStep(grid, firstRowKey, rowCount)) return }
}

/** One pass of [revealOfflineRows]. True when the list should be measured again, because it moved a sliver only. */
private suspend fun revealStep(
    grid: LazyGridState,
    firstRowKey: Any,
    rowCount: Int,
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
                    rowCount = rowCount,
                    viewportEnd = info.viewportEndOffset,
                )
            if (delta != 0) grid.animateScrollBy(delta.toFloat())
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

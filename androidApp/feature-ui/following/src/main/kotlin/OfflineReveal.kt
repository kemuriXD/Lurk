package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The lazy key of the Offline header item. */
internal const val OFFLINE_HEADER_KEY = "offline_header"

/** Stands in for the first row's height until the row has been measured. */
private val EstimatedRowHeight = 88.dp

/** Scroll, measure and scroll again: the first pass may run on an estimate, the second sees the real row. */
private const val REVEAL_PASSES = 3

/** Frames to let the new rows join the list before measuring. */
private const val SETTLE_FRAMES = 2

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
 * a fold, nor when the row is already in view.
 */
@Composable
internal fun RevealOfflineRowsOnUnfold(
    grid: LazyGridState,
    collapsed: Boolean,
    firstRowKey: Any?,
) {
    var wasCollapsed by remember { mutableStateOf(collapsed) }
    val estimate = with(LocalDensity.current) { EstimatedRowHeight.roundToPx() }
    LaunchedEffect(collapsed) {
        val unfolded = wasCollapsed && !collapsed
        wasCollapsed = collapsed
        if (!unfolded || firstRowKey == null) return@LaunchedEffect
        // The rows join the list on the next layout pass; wait for that before measuring anything.
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        repeat(REVEAL_PASSES) {
            val info = grid.layoutInfo
            val header = info.visibleItemsInfo.firstOrNull { it.key == OFFLINE_HEADER_KEY } ?: return@LaunchedEffect
            val row = info.visibleItemsInfo.firstOrNull { it.key == firstRowKey }
            val delta =
                offlineRevealScroll(
                    headerTop = header.offset.y,
                    headerHeight = header.size.height,
                    rowHeight = row?.size?.height ?: estimate,
                    viewportEnd = info.viewportEndOffset - info.afterContentPadding,
                )
            if (delta == 0) return@LaunchedEffect
            grid.animateScrollBy(delta.toFloat())
        }
    }
}

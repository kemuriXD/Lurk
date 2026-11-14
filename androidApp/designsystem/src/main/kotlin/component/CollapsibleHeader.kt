package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** A window shorter than this is a landscape phone: a pinned header would leave the list a sliver. */
private const val COMPACT_HEIGHT_DP = 480

/** True in a compact-height window (a phone in landscape). */
@Composable
fun rememberCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP

/**
 * A [header] above a [body] list. When [collapsible], scrolling the body up slides the header away and scrolling it
 * down brings the header back, so a landscape phone gives the list the whole height. When not, the header stays put.
 *
 * The header is never stranded. It is shown in full again as soon as nothing can bring it back by scrolling: when the
 * window stops being [collapsible], when the body has no room to scroll ([canScroll] is false: an empty state, an
 * error, a short list), and whenever [resetKey] changes (a new search puts the list back at its top). While the body
 * can't scroll, the header doesn't collapse at all. The slide is not saved, so a screen made again starts with the
 * header shown. [scrollRoom] is asked at the moment of a scroll: a list too short to scroll never collapses the header.
 */
@Composable
fun CollapsibleHeader(
    collapsible: Boolean,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true,
    scrollRoom: () -> Boolean = { true },
    resetKey: Any? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val full = remember { floatArrayOf(0f) }
    val room by rememberUpdatedState(scrollRoom)
    // Whether the header is shown in full whatever the slide says. Only what is known while composing is used here:
    // the list measures after the header, so reading its scroll state in the layout would make it relayout forever.
    val revealed = !collapsible || !canScroll
    val revealedNow by rememberUpdatedState(revealed)
    // A window that stops being compact, or a new [resetKey], starts again from a fully shown header.
    LaunchedEffect(collapsible, resetKey) { offset = 0f }
    val connection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (revealedNow || !room()) return Offset.Zero
                    val from = offset
                    val next = (from + available.y).coerceIn(-full[0], 0f)
                    val used = next - from
                    offset = next
                    return Offset(0f, used)
                }
            }
        }
    Column(modifier.then(if (collapsible) Modifier.nestedScroll(connection) else Modifier)) {
        Box(
            Modifier
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                    full[0] = placeable.height.toFloat()
                    val shift = if (revealed) 0 else offset.coerceIn(-full[0], 0f).roundToInt()
                    layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) {
                        placeable.placeRelative(0, shift)
                    }
                },
        ) { header() }
        body()
    }
}

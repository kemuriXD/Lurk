package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 */
@Composable
fun CollapsibleHeader(
    collapsible: Boolean,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    val full = remember { floatArrayOf(0f) }
    val connection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val next = (offset + available.y).coerceIn(-full[0], 0f)
                    val used = next - offset
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
                    val shift = if (collapsible) offset.coerceIn(-full[0], 0f).roundToInt() else 0
                    layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) {
                        placeable.placeRelative(0, shift)
                    }
                },
        ) { header() }
        body()
    }
}

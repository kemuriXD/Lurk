package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import kotlin.math.sqrt

internal const val HOST = "layout_host"

internal val Phone = 360.dp

/** Shows [content] in a box [width] wide, with text scaled by [fontScale]. The box is the host the checks measure. */
@Composable
internal fun LayoutHost(
    width: Dp,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        Box(Modifier.width(width).testTag(HOST)) { content() }
    }
}

internal fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

/** Whether [point] lies inside [rect] with its four corners rounded by [radius]. */
internal fun insideRounded(
    point: Offset,
    rect: Rect,
    radius: Float,
): Boolean {
    if (point.x !in rect.left..rect.right || point.y !in rect.top..rect.bottom) return false
    val cx = point.x.coerceIn(rect.left + radius, rect.right - radius)
    val cy = point.y.coerceIn(rect.top + radius, rect.bottom - radius)
    val dx = point.x - cx
    val dy = point.y - cy
    return sqrt(dx * dx + dy * dy) <= radius + 0.01f
}

/** Asserts every corner of [inner] lies inside [outer] once its corners are rounded by [radius] pixels. */
internal fun assertInsideRounded(
    what: String,
    inner: Rect,
    outer: Rect,
    radius: Float,
) {
    val corners = listOf(inner.topLeft, inner.topRight, inner.bottomLeft, inner.bottomRight)
    val outside = corners.filterNot { insideRounded(it, outer, radius) }
    assertTrue("$what $inner pokes out of $outer (corner radius $radius): $outside", outside.isEmpty())
}

/** The text layout of a node that shows text (the unmerged node of a `Text`). */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.first()
}

/** Asserts [layout] shows all its text, and that no line ends inside a word. */
internal fun assertWrapsWholeWords(
    what: String,
    layout: TextLayoutResult,
) {
    val text = layout.layoutInput.text.text
    assertTrue("$what is cut off: ${layout.lineCount} lines of \"$text\"", !layout.hasVisualOverflow)
    for (line in 0 until layout.lineCount) {
        val end = layout.getLineEnd(line, visibleEnd = true)
        val atWordEdge = end >= text.length || text[end - 1] == ' ' || text[end] == ' '
        assertTrue("$what breaks inside a word at line $line of \"$text\"", atWordEdge)
    }
}

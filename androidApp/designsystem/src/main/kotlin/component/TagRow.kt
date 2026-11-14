package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius

private val TagGap = 6.dp

/**
 * How many tags, measured [widths] wide, fit in [available] with [gap] between chips. When some don't fit, room is
 * kept for the "+N" chip, [moreWidth] wide for N hidden tags.
 */
internal fun tagsThatFit(
    widths: List<Int>,
    available: Int,
    gap: Int,
    moreWidth: (hidden: Int) -> Int,
): Int {
    if (widths.sum() + gap * (widths.size - 1) <= available) return widths.size
    var used = 0
    var fits = 0
    for ((index, width) in widths.withIndex()) {
        used += width + gap
        val hidden = widths.size - index - 1
        if (hidden == 0 || used + moreWidth(hidden) > available) break
        fits = index + 1
    }
    return fits
}

/**
 * A stream's tags as small tonal chips on one line. The ones that don't fit are counted in a closing "+N" chip. When
 * not even the first tag fits beside that chip, it shows cut short with an ellipsis rather than not at all.
 *
 * Every tag is measured in a silent pass first; only the chips that show are composed for real, so TalkBack reads
 * exactly what is on screen.
 */
@Composable
internal fun TagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val gap = TagGap.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val widths =
            subcompose("measure") { tags.forEach { Chip(it, Modifier.clearAndSetSemantics {}) } }
                .map { it.measure(loose).width }
        val moreWidths = mutableMapOf<Int, Int>()

        fun moreWidth(hidden: Int): Int =
            moreWidths.getOrPut(hidden) {
                subcompose("measure:+$hidden") { MoreChip(hidden, Modifier.clearAndSetSemantics {}) }
                    .single()
                    .measure(loose)
                    .width
            }

        val fits = tagsThatFit(widths, constraints.maxWidth, gap, ::moreWidth)
        // Not even one whole tag fits beside "+N": the first one shows cut short instead.
        val count = fits.coerceAtLeast(1)
        val hidden = tags.size - count
        val room = constraints.maxWidth - if (hidden > 0) moreWidth(hidden) + gap else 0
        val shown =
            subcompose("shown") {
                tags.take(count).forEach { Chip(it) }
                if (hidden > 0) MoreChip(hidden, Modifier.testTag(ExpandedStreamCardTags.MORE_TAGS))
            }.mapIndexed { index, chip ->
                val last = index == count - 1 && fits == 0
                chip.measure(if (last) Constraints(maxWidth = room.coerceAtLeast(0)) else loose)
            }
        val height = shown.maxOf { it.height }
        layout(shown.sumOf { it.width } + gap * (shown.size - 1), height) {
            var x = 0
            shown.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}

@Composable
private fun MoreChip(
    hidden: Int,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.cd_more_tags, hidden, hidden)
    Chip(text = "+$hidden", modifier = modifier.semantics { contentDescription = description })
}

@Composable
private fun Chip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(LurkRadius.sm))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

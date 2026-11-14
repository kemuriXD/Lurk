package io.github.kemurixd.lurk.featureui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.feature.search.SearchType

/** All / Channels / Categories, in the same segmented style as Browse's Top Live / Categories switch. */
@Composable
internal fun TypeSwitch(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
) {
    val types = SearchType.entries
    // At large type three segments can't hold their words on one line, so each gets a row of its own: all the same
    // height, never broken mid-word.
    val stacked = LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    val segment: @Composable SingleChoiceSegmentedButtonRowScope.(Int, SearchType) -> Unit = { index, type ->
        SegmentedButton(
            selected = type == selected,
            onClick = { onSelect(type) },
            shape = SegmentedButtonDefaults.itemShape(if (stacked) 0 else index, if (stacked) 1 else types.size),
            modifier = Modifier.testTag(type.tag()).heightIn(min = 48.dp),
        ) {
            Text(
                text =
                    stringResource(
                        when (type) {
                            SearchType.ALL -> R.string.type_all
                            SearchType.CHANNELS -> R.string.type_channels
                            SearchType.CATEGORIES -> R.string.type_categories
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (stacked) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            types.forEachIndexed { index, type ->
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { segment(index, type) }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            types.forEachIndexed { index, type -> segment(index, type) }
        }
    }
}

private const val STACKED_FONT_SCALE = 1.25f

private fun SearchType.tag(): String =
    when (this) {
        SearchType.ALL -> SearchTestTags.TYPE_ALL
        SearchType.CHANNELS -> SearchTestTags.TYPE_CHANNELS
        SearchType.CATEGORIES -> SearchTestTags.TYPE_CATEGORIES
    }

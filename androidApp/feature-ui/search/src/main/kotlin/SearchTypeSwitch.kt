package io.github.kemurixd.lurk.featureui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.feature.search.SearchType

/**
 * All / Channels / Categories, in the same segmented style as Browse's Top Live / Categories switch. When the three
 * words can't sit on one row at this width and type size, each segment gets a row of its own: all the same height,
 * never broken mid-word. The three rows stay one radio group of three for screen readers.
 */
@Composable
internal fun TypeSwitch(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
) {
    val types = SearchType.entries
    val labels = types.map { stringResource(it.label()) }
    val needed = segmentWidths(types, labels, selected)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val row = maxWidth - SwitchInset * 2
        val stacked = needed.any { it > row / types.size }
        val segment: @Composable SingleChoiceSegmentedButtonRowScope.(Int, SearchType) -> Unit = { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(if (stacked) 0 else index, if (stacked) 1 else types.size),
                modifier =
                    Modifier
                        .testTag(type.tag())
                        .heightIn(min = 48.dp)
                        .then(
                            if (stacked) {
                                Modifier.semantics {
                                    collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                                }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Text(text = labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (stacked) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SwitchInset, vertical = 4.dp)
                    .selectableGroup()
                    .semantics { collectionInfo = CollectionInfo(types.size, 1) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                types.forEachIndexed { index, type ->
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { segment(index, type) }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = SwitchInset, vertical = 4.dp)) {
                types.forEachIndexed { index, type -> segment(index, type) }
            }
        }
    }
}

/**
 * Each segment's word and its own padding, plus the check icon on the selected one, to hold against a third of the
 * row: a word that fits until its segment is picked would otherwise lose its end to the icon ("Categor…").
 */
@Composable
private fun segmentWidths(
    types: List<SearchType>,
    labels: List<String>,
    selected: SearchType,
): List<Dp> {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    return with(LocalDensity.current) {
        types.mapIndexed { index, type ->
            val word =
                measurer
                    .measure(labels[index], style, maxLines = 1)
                    .size.width
                    .toDp()
            word + SegmentPadding + if (type == selected) CheckIconSpace else 0.dp
        }
    }
}

private val SwitchInset = 16.dp

/** A segment's horizontal padding, both sides. */
private val SegmentPadding = 24.dp

/** The selected segment's check icon and the gap after it. */
private val CheckIconSpace = SegmentedButtonDefaults.IconSize + 8.dp

@StringRes
private fun SearchType.label(): Int =
    when (this) {
        SearchType.ALL -> R.string.type_all
        SearchType.CHANNELS -> R.string.type_channels
        SearchType.CATEGORIES -> R.string.type_categories
    }

private fun SearchType.tag(): String =
    when (this) {
        SearchType.ALL -> SearchTestTags.TYPE_ALL
        SearchType.CHANNELS -> SearchTestTags.TYPE_CHANNELS
        SearchType.CATEGORIES -> SearchTestTags.TYPE_CATEGORIES
    }

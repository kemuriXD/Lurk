package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val HeaderPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)

/** Test tags on [SectionHeader]. */
object SectionHeaderTags {
    const val COUNT = "section_header_count"
}

/**
 * A list section title with an optional count beside it and an optional action on the right (Figma SectionHeader).
 * With [onClick] the whole row is one control: the heading, the count and [trailing] form a single stop.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val whole = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).semantics { heading() }
    val title = if (onClick == null) Modifier.semantics(mergeDescendants = true) { heading() } else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(whole)
                .padding(HeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).then(title),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).testTag(SectionHeaderTags.COUNT),
                )
            }
        }
        // Half the width at most, so a wide action never squeezes the title below its words.
        if (trailing != null) {
            Box(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth / 2))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                },
            ) { trailing() }
        }
    }
}

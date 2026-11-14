package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius

private const val TEXT_LINES = 2
private val TextInset = 4.dp
private const val ART_WIDTH = 116f
private const val ART_HEIGHT = 155f

/** A category with its box art, name and viewer count (Figma CategoryTile). The badge shows only with [showPlatform]. */
@Composable
fun CategoryTile(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlatform: Boolean = true,
) {
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ART_WIDTH / ART_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = category.boxArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (showPlatform) {
                PlatformBadge(category.platform, Modifier.align(Alignment.TopStart).padding(LurkRadius.sm))
            }
        }
        // The tile is clipped to rounded corners, so the text keeps off the bottom corners.
        Column(
            modifier = Modifier.cornerInset(TextInset),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = TEXT_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            category.viewers?.let {
                Text(
                    text = stringResource(R.string.viewers_count, formatViewers(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = TEXT_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.designsystem.R

private val RowPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 24.dp, bottom = 12.dp)

/**
 * One channel in a list (Figma ChannelRow). Shows the LIVE badge and viewers while live, and a ring on the avatar
 * unless [showLiveRing] is off.
 */
@Composable
fun ChannelRow(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLiveRing: Boolean = true,
) {
    val live = channel.live
    val category = live?.category
    val supporting =
        when {
            live == null -> {
                stringResource(R.string.offline)
            }

            category != null -> {
                stringResource(R.string.supporting_category_viewers, category.name, formatViewers(live.viewers))
            }

            else -> {
                stringResource(R.string.viewers_count, formatViewers(live.viewers))
            }
        }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(RowPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LurkAvatar(channel.avatarUrl, channel.displayName, size = 40.dp, liveRing = live != null && showLiveRing)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PlatformBadge(channel.ref.platform)
            }
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (live != null) LiveBadge()
    }
}

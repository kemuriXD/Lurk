package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.feature.common.PlatformFilter

/** All / Twitch / Kick chips; exactly one is selected (Figma FilterChip). */
@Composable
fun PlatformFilterChips(
    selected: PlatformFilter,
    onSelect: (PlatformFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlatformFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) },
                leadingIcon =
                    if (filter == selected) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun PlatformFilter.label(): String =
    when (this) {
        PlatformFilter.ALL -> stringResource(R.string.filter_all)
        PlatformFilter.TWITCH -> Platform.TWITCH.label()
        PlatformFilter.KICK -> Platform.KICK.label()
    }

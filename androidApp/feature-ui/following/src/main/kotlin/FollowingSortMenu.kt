package io.github.kemurixd.lurk.featureui.following

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.feature.following.FollowingSort
import io.github.kemurixd.lurk.designsystem.R as DesignR

@StringRes
private fun FollowingSort.label(): Int =
    when (this) {
        FollowingSort.VIEWERS -> R.string.sort_viewers
        FollowingSort.RECENT -> R.string.sort_recent
        FollowingSort.NAME -> R.string.sort_name
    }

/** The sort action in the Live header (Figma SectionHeader sort action and the sort menu). */
@Composable
internal fun FollowingSortMenu(
    selected: FollowingSort,
    onSelect: (FollowingSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val currentLabel = stringResource(selected.label())
    val description = stringResource(R.string.cd_sort, currentLabel)
    TextButton(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(DesignR.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(currentLabel)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        FollowingSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(stringResource(sort.label())) },
                leadingIcon = {
                    if (sort == selected) {
                        Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                onClick = {
                    open = false
                    onSelect(sort)
                },
            )
        }
    }
}

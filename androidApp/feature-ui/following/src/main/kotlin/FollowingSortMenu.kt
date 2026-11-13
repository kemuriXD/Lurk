package io.github.kemurixd.lurk.featureui.following

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kemurixd.lurk.designsystem.component.SortMenu
import io.github.kemurixd.lurk.feature.following.FollowingSort

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
    SortMenu(
        selected = selected,
        options = FollowingSort.entries,
        label = { stringResource(it.label()) },
        onSelect = onSelect,
        modifier = modifier,
    )
}

package io.github.kemurixd.lurk.designsystem.component

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
import io.github.kemurixd.lurk.designsystem.R

/**
 * The sort action at the right of a list's section header, and the menu it opens (Figma SectionHeader sort action and
 * the sort menu). The button shows the [selected] option's [label]; the menu lists [options] with a check on it.
 */
@Composable
fun <T> SortMenu(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val currentLabel = label(selected)
    val description = stringResource(R.string.cd_sort, currentLabel)
    TextButton(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(currentLabel)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(label(option)) },
                leadingIcon = {
                    if (option == selected) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                onClick = {
                    open = false
                    onSelect(option)
                },
            )
        }
    }
}

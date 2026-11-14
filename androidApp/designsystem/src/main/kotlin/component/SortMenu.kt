package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.designsystem.R

/**
 * The sort action at the right of a list's section header, and the menu it opens (Figma SectionHeader sort action and
 * the sort menu). The button shows the [selected] option's [label]; [description] names it for screen readers,
 * given that label. The menu lists [options] with a check on the selected one.
 */
@Composable
fun <T> SortMenu(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    description: @Composable (current: String) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val currentLabel = label(selected)
    val buttonDescription = description(currentLabel)
    Box(modifier) {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = buttonDescription },
        ) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Figma Sort menu: surface-container card, 8 dp radius, level 2 shadow, 48 dp rows with a 24 dp check slot.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = MENU_MIN_WIDTH),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = MENU_ELEVATION,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        if (option == selected) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private val MENU_MIN_WIDTH = 208.dp
private val MENU_ELEVATION = 3.dp

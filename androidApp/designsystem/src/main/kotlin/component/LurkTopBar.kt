package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.designsystem.R

/** Screen title with the account button on the right (Figma TopAppBar). */
@Composable
fun LurkTopBar(
    title: String,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val hasNavigation = navigationIcon != null
    val titleInset = if (hasNavigation) 4.dp else 0.dp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = if (hasNavigation) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = titleInset).semantics { heading() },
        )
        IconButton(onClick = onAccount) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = stringResource(R.string.cd_account_and_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

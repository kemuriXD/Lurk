package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.messageRes

private val ChipPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
private val ChipPaddingNoAction = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val ChipShape = RoundedCornerShape(24.dp)

/**
 * Pill shown when one platform failed but the others loaded (Figma InlineErrorChip). It names the failure in [title]
 * and gives the reason under it. Retry shows unless the caller sets [showRetry] false, which it does only where
 * asking again can't help, such as a load-more that has ended paging.
 */
@Composable
fun InlineErrorChip(
    platform: Platform,
    error: LurkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = R.string.error_chip_message,
    showRetry: Boolean = true,
) {
    val name = platform.label()
    val reason = stringResource(error.messageRes())
    val retryLabel = stringResource(R.string.error_chip_retry_cd, name)
    val onContainer = MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.errorContainer, ChipShape)
                .padding(if (showRetry) ChipPadding else ChipPaddingNoAction)
                .semantics { stateDescription = reason },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(platform)
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = stringResource(title, name),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
            Text(text = reason, style = MaterialTheme.typography.bodySmall, color = onContainer)
        }
        if (showRetry) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryLabel },
                colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
            ) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.error_chip_retry), Modifier.padding(start = 8.dp))
            }
        }
    }
}

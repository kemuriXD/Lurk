package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform

/** A filled or text button whose [label] leads with [logo]'s platform logo, when there is one. */
@Composable
internal fun LogoActionButton(
    label: String,
    logo: Platform?,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Boolean = false,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding =
                if (logo !=
                    null
                ) {
                    ButtonDefaults.ButtonWithIconContentPadding
                } else {
                    ButtonDefaults.ContentPadding
                },
        ) {
            logo?.let { PlatformLogoLeading(it) }
            if (progress) RetryProgress()
            Text(label)
        }
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding =
                if (logo != null) {
                    ButtonDefaults.TextButtonWithIconContentPadding
                } else {
                    ButtonDefaults.TextButtonContentPadding
                },
        ) {
            logo?.let { PlatformLogoLeading(it) }
            if (progress) RetryProgress()
            Text(label)
        }
    }
}

/** The small spinner that leads a Retry label while the retry runs. */
@Composable
private fun RetryProgress() {
    CircularProgressIndicator(
        modifier = Modifier.padding(end = 8.dp).size(16.dp).testTag(ErrorTags.RETRY_PROGRESS),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}

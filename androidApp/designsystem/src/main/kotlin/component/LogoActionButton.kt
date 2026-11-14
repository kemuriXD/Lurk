package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
            LabelOrProgress(label, progress)
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
            LabelOrProgress(label, progress)
        }
    }
}

/**
 * The label, or while [progress] a small spinner in its place. The label stays in the layout, invisible, so the button
 * keeps its width and nothing around it reflows.
 */
@Composable
private fun LabelOrProgress(
    label: String,
    progress: Boolean,
) {
    Box(contentAlignment = Alignment.Center) {
        Text(label, modifier = Modifier.alpha(if (progress) 0f else 1f))
        if (progress) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).testTag(ErrorTags.RETRY_PROGRESS),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
    }
}

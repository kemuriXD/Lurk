package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.designsystem.R

/** The 6dp dot that marks a live channel. */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Spacer(modifier.size(6.dp).background(MaterialTheme.colorScheme.onError, CircleShape))
}

/** The red LIVE pill (Figma LiveBadge). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .height(20.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot()
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.live_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

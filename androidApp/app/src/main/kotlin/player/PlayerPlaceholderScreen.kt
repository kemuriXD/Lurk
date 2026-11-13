package io.github.kemurixd.lurk.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.navigation.BackButton
import io.github.kemurixd.lurk.navigation.PlayerRoute

private const val VIDEO_ASPECT = 16f / 9f

/** Stands in for the player until P7b: the stream's thumbnail, its title and a way to the channel page. */
@Composable
fun PlayerPlaceholderScreen(
    route: PlayerRoute,
    onGoToChannel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackButton(onBack)
        AsyncImage(
            model = route.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_ASPECT)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = route.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onGoToChannel) { Text(stringResource(R.string.player_go_to_channel)) }
        }
    }
}

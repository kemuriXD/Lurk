package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val RingWidth = 2.dp
private val RingGap = 4.dp
private val LargeAvatar = 40.dp

/** Round channel picture with an initial as the fallback, and an optional red ring while live. */
@Composable
fun LurkAvatar(
    imageUrl: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    liveRing: Boolean = false,
) {
    val initialStyle =
        if (size >= LargeAvatar) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium
    val ring = if (liveRing) Modifier.border(RingWidth, MaterialTheme.colorScheme.error, CircleShape) else Modifier
    Box(modifier.size(size).then(ring), contentAlignment = Alignment.Center) {
        val inner = if (liveRing) Modifier.padding(RingGap / 2).size(size - RingGap) else Modifier.size(size)
        Surface(
            modifier = inner.clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            if (imageUrl == null) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.firstOrNull()?.uppercase().orEmpty(),
                        style = initialStyle,
                    )
                }
            } else {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
    }
}

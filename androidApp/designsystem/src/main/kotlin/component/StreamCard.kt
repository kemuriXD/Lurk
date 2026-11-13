package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius
import io.github.kemurixd.lurk.designsystem.theme.PlayerColors

private val ThumbnailWidth = 160.dp
private val ThumbnailHeight = 90.dp
private val CardPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
private val OverlayInset = 6.dp
private const val TITLE_LINES = 2

/**
 * A live stream in the list layout (Figma StreamCard, Layout=List). The avatar and the channel name
 * open the channel; everything else opens the player.
 */
@Composable
fun StreamCard(
    stream: Stream,
    onOpenPlayer: () -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("stream_card")
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onOpenPlayer)
                .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StreamThumbnail(stream, Modifier.size(ThumbnailWidth, ThumbnailHeight))
        StreamInfo(stream, onOpenChannel, Modifier.weight(1f))
    }
}

/**
 * The request for a preview at [url]. While it loads, the image already shown from [shown], if it is still in the
 * memory cache, stays as the placeholder, so a refreshed preview URL doesn't flash the grey box.
 */
internal fun thumbnailRequest(
    context: PlatformContext,
    url: String?,
    shown: String?,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url)
        .placeholderMemoryCacheKey(shown?.takeIf { it != url })
        .build()

/** The preview URL a thumbnail last showed. */
private class ShownPreview {
    var url: String? = null
}

/**
 * A stream's picture with the LIVE badge top left and the viewer count bottom left on a scrim. The size comes from
 * [modifier]. [content] draws on top, for example a play button.
 */
@Composable
fun StreamThumbnail(
    stream: Stream,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LurkRadius.md),
    overlayInset: Dp = OverlayInset,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalPlatformContext.current
    // The preview URL changes every few minutes so the image refreshes; the last one shown stays up while it loads.
    // Plain memory, not state: a preview landing changes nothing on screen, so it must not recompose the thumbnail.
    val shown = remember(stream.channel) { ShownPreview() }
    val url = stream.thumbnailUrl
    // One request per URL, so other changes to the stream never rebuild it or restart the load.
    val request = remember(context, url) { thumbnailRequest(context, url, shown.url) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(
            model = request,
            onSuccess = { shown.url = url },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        LiveBadge(Modifier.align(Alignment.TopStart).padding(overlayInset))
        Text(
            text = stringResource(R.string.viewers_count, formatViewers(stream.viewers)),
            style = MaterialTheme.typography.labelSmall,
            color = PlayerColors.onScrim,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(overlayInset)
                    .clip(RoundedCornerShape(LurkRadius.xs))
                    .background(PlayerColors.scrim)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        content()
    }
}

@Composable
private fun StreamInfo(
    stream: Stream,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openChannelLabel = stringResource(R.string.cd_open_channel, stream.displayName)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LurkAvatar(
                imageUrl = stream.avatarUrl,
                name = stream.displayName,
                size = 32.dp,
                modifier =
                    Modifier
                        .testTag("stream_card_avatar")
                        .semantics { contentDescription = openChannelLabel }
                        .clickable(role = Role.Button, onClick = onOpenChannel),
            )
            Text(
                text = stream.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("stream_card_name")
                        .clickable(role = Role.Button, onClick = onOpenChannel),
            )
            PlatformBadge(stream.channel.platform)
        }
        Text(
            text = stream.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("stream_card_title"),
        )
        stream.category?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Static skeleton shown while streams load (Figma StreamCard, State=Loading). Not interactive. */
@Composable
fun StreamCardPlaceholder(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val bone = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("stream_card_placeholder")
                .semantics { contentDescription = loading }
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(ThumbnailWidth, ThumbnailHeight).clip(RoundedCornerShape(LurkRadius.md)).background(bone))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bone(110.dp, 14.dp, bone)
            Bone(150.dp, 12.dp, bone)
            Bone(120.dp, 12.dp, bone)
            Bone(80.dp, 10.dp, bone)
        }
    }
}

@Composable
private fun Bone(
    width: Dp,
    height: Dp,
    color: Color,
) {
    Spacer(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(LurkRadius.xs))
            .background(color),
    )
}

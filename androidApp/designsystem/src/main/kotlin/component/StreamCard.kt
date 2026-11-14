package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
private const val THUMBNAIL_ASPECT = 16f / 9f

/** The gap between the picture and the text, beside or under it. */
private val PartGap = 12.dp

/**
 * The narrowest the text beside the picture may get at 1x type, growing with the font scale. Below it the name would
 * shrink to a letter or two, so the card stacks: the picture on top, the text under it. A 360 dp phone at 1x keeps the
 * Figma row; a 320 dp phone at 1.15x stacks.
 */
private val MinTextBeside = 120.dp

/** Whether a card this wide stacks its text under the picture at this font scale. */
private fun stacks(
    cardWidth: Dp,
    fontScale: Float,
): Boolean {
    val padding =
        CardPadding.calculateLeftPadding(LayoutDirection.Ltr) + CardPadding.calculateRightPadding(LayoutDirection.Ltr)
    return cardWidth - padding - ThumbnailWidth - PartGap < MinTextBeside * fontScale
}

/**
 * A live stream in the list layout (Figma StreamCard, Layout=List). The avatar and the channel name
 * open the channel; everything else opens the player. The platform badge shows only while [showPlatform] is on: a
 * list filtered to one platform already says which it is. On a card too narrow for the text beside the picture
 * ([MinTextBeside]), the text goes under it.
 */
@Composable
fun StreamCard(
    stream: Stream,
    onOpenPlayer: () -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
    showPlatform: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val card =
            Modifier
                .fillMaxWidth()
                .testTag("stream_card")
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onOpenPlayer)
                .padding(CardPadding)
        if (stacks(maxWidth, LocalDensity.current.fontScale)) {
            Column(card, verticalArrangement = Arrangement.spacedBy(PartGap)) {
                StreamThumbnail(stream, Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_ASPECT))
                StreamInfo(stream, onOpenChannel, showPlatform, Modifier.fillMaxWidth())
            }
        } else {
            Row(card, horizontalArrangement = Arrangement.spacedBy(PartGap)) {
                StreamThumbnail(stream, Modifier.size(ThumbnailWidth, ThumbnailHeight))
                StreamInfo(stream, onOpenChannel, showPlatform, Modifier.weight(1f))
            }
        }
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

/** A stream's preview picture alone, cropped to fill [modifier]'s size. Not described: the card around it is. */
@Composable
internal fun StreamPreviewImage(
    stream: Stream,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    // The preview URL changes every few minutes so the image refreshes; the last one shown stays up while it loads.
    // Plain memory, not state: a preview landing changes nothing on screen, so it must not recompose the thumbnail.
    val shown = remember(stream.channel) { ShownPreview() }
    val url = stream.thumbnailUrl
    // One request per URL, so other changes to the stream never rebuild it or restart the load.
    val request = remember(context, url) { thumbnailRequest(context, url, shown.url) }
    AsyncImage(
        model = request,
        onSuccess = { shown.url = url },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * A stream's picture with the live dot top left and the viewer count bottom left on a scrim. The size comes from
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
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        StreamPreviewImage(stream, Modifier.fillMaxSize())
        // One accessibility node for both overlays: the dot carries "Live, 12.4K viewers" and the count stays silent.
        val viewers = formatViewers(stream.viewers)
        val liveViewers = stringResource(R.string.cd_live_viewers, viewers)
        LiveDot(
            Modifier
                .align(Alignment.TopStart)
                .padding(overlayInset)
                .semantics { contentDescription = liveViewers },
            size = 10.dp,
            describe = false,
        )
        // One line whatever the font size: the badge grows with the text up to 1.3x, then stops, so it never wraps over
        // the picture.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(BADGE_MAX_FONT_SCALE)),
        ) {
            Text(
                text = stringResource(R.string.viewers_count, viewers),
                style = MaterialTheme.typography.labelSmall,
                color = PlayerColors.onScrim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(overlayInset)
                        .clip(RoundedCornerShape(LurkRadius.xs))
                        .background(PlayerColors.scrim)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        // The dot already says "Live, 12.4K viewers", so screen readers skip the count.
                        .clearAndSetSemantics { testTag = StreamCardTags.VIEWERS },
            )
        }
        content()
    }
}

@Composable
private fun StreamInfo(
    stream: Stream,
    onOpenChannel: () -> Unit,
    showPlatform: Boolean,
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
                        .clickable(role = Role.Button, onClick = onOpenChannel)
                        // The avatar is the channel's one labelled stop for accessibility; this is the same action.
                        .semantics { hideFromAccessibility() },
            )
            if (showPlatform) PlatformBadge(stream.channel.platform)
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

/**
 * Static skeleton shown while streams load (Figma StreamCard, State=Loading). Not interactive. It stacks where
 * [StreamCard] does, so the loaded cards take its place without a jump.
 */
@Composable
fun StreamCardPlaceholder(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val bone = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val card =
            Modifier
                .fillMaxWidth()
                .testTag("stream_card_placeholder")
                .semantics { contentDescription = loading }
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(CardPadding)
        val picture = Modifier.clip(RoundedCornerShape(LurkRadius.md)).background(bone)
        if (stacks(maxWidth, LocalDensity.current.fontScale)) {
            Column(card, verticalArrangement = Arrangement.spacedBy(PartGap)) {
                Spacer(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_ASPECT).then(picture))
                Bones(bone)
            }
        } else {
            Row(card, horizontalArrangement = Arrangement.spacedBy(PartGap)) {
                Spacer(Modifier.size(ThumbnailWidth, ThumbnailHeight).then(picture))
                Bones(bone)
            }
        }
    }
}

@Composable
private fun Bones(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bone(110.dp, 14.dp, color)
        Bone(150.dp, 12.dp, color)
        Bone(120.dp, 12.dp, color)
        Bone(80.dp, 10.dp, color)
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

private const val BADGE_MAX_FONT_SCALE = 1.3f

object StreamCardTags {
    /** The "12.4K viewers" badge over a thumbnail. */
    const val VIEWERS = "stream_viewers_badge"
}

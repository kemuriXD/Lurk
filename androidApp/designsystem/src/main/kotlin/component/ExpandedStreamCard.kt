package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius
import io.github.kemurixd.lurk.designsystem.time.UptimeText
import io.github.kemurixd.lurk.designsystem.time.rememberUptimeMinutes
import io.github.kemurixd.lurk.designsystem.time.spokenUptime
import kotlin.time.Duration.Companion.minutes

private const val PREVIEW_RATIO = 16f / 9f
private const val NAME_SHARE = 0.6f
private val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val AvatarSize = 40.dp
private val MetaIconSize = 16.dp
private val HeadlineGap = 6.dp

/** Test tags on [ExpandedStreamCard]. */
object ExpandedStreamCardTags {
    const val CARD = "expanded_stream_card"
    const val PREVIEW = "expanded_stream_card_preview"
    const val AVATAR = "expanded_stream_card_avatar"
    const val NAME = "expanded_stream_card_name"
    const val TITLE = "expanded_stream_card_title"
    const val UPTIME = "expanded_stream_card_uptime"
    const val TAGS = "expanded_stream_card_tags"
    const val MORE_TAGS = "expanded_stream_card_more_tags"
    const val PLACEHOLDER = "expanded_stream_card_placeholder"
}

/**
 * A followed live stream with a big preview, for the Following tab. Under the clean 16:9 preview: the avatar, then
 * the channel name and the muted title on one line with the platform badge at its end, then the live dot, uptime,
 * viewers and category, then the stream's tags. The badge, and the platform in the spoken sentence, show only with
 * [showPlatform]. The avatar and the name open the channel; everything else opens the player. The avatar is the
 * channel's one labelled stop for accessibility.
 */
@Composable
fun ExpandedStreamCard(
    stream: Stream,
    onOpenPlayer: () -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
    showPlatform: Boolean = true,
) {
    val openChannel = stringResource(R.string.cd_open_channel, stream.displayName)
    val description = rememberCardDescription(stream, showPlatform)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ExpandedStreamCardTags.CARD)
                .clickable(onClick = onOpenPlayer)
                .semantics { contentDescription = description() }
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StreamPreviewImage(
            stream,
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(RoundedCornerShape(LurkRadius.lg))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag(ExpandedStreamCardTags.PREVIEW),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LurkAvatar(
                imageUrl = stream.avatarUrl,
                name = stream.displayName,
                size = AvatarSize,
                modifier =
                    Modifier
                        .testTag(ExpandedStreamCardTags.AVATAR)
                        .semantics { contentDescription = openChannel }
                        .clickable(role = Role.Button, onClick = onOpenChannel),
            )
            // A merge boundary of its own, hidden from screen readers: the card's description says all of this
            // already, and being a boundary keeps these texts out of the card's merged node.
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) { hideFromAccessibility() },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Headline(stream, onOpenChannel, showPlatform)
                MetaLine(stream)
                if (stream.tags.isNotEmpty()) {
                    TagRow(stream.tags, Modifier.padding(top = 2.dp).testTag(ExpandedStreamCardTags.TAGS))
                }
            }
        }
    }
}

/**
 * What a screen reader says for the card, as one sentence: the channel name, the title, how long it has been live,
 * the viewers, the category, the tags and the platform. The pieces under the avatar say nothing themselves, so nothing
 * is read twice. The uptime is spoken as a duration at minute resolution; its clock is read only when a screen reader
 * asks, so a tick never recomposes the card.
 */
@Composable
private fun rememberCardDescription(
    stream: Stream,
    showPlatform: Boolean,
): () -> String {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val minutes = rememberUptimeMinutes(stream.startedAt)
    val separator = stringResource(R.string.cd_card_separator)
    val live = stringResource(R.string.cd_live)
    val viewers = stringResource(R.string.viewers_count, formatViewerCount(stream.viewers, locale))
    val platform = stringResource(stream.channel.platform.labelRes)
    return {
        val startedAt = stream.startedAt
        listOfNotNull(
            stream.displayName,
            stream.title.takeIf { it.isNotBlank() },
            if (startedAt == null) live else spokenUptime(minutes.value.minutes, resources),
            viewers,
            stream.category?.name,
        ).plus(stream.tags).plus(listOfNotNull(platform.takeIf { showPlatform })).joinToString(separator)
    }
}

/**
 * The name, the title and the badge on one line. The name takes what it needs, up to [NAME_SHARE] of the room, the
 * title takes the rest, and the badge always keeps its place at the end.
 */
@Composable
private fun Headline(
    stream: Stream,
    onOpenChannel: () -> Unit,
    showPlatform: Boolean,
) {
    val style = MaterialTheme.typography.titleSmall
    Layout(
        content = {
            Text(
                text = stream.displayName,
                style = style.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .testTag(ExpandedStreamCardTags.NAME)
                        .clickable(role = Role.Button, onClick = onOpenChannel)
                        // The avatar is the channel's one labelled stop for accessibility; this is the same action.
                        .semantics { hideFromAccessibility() },
            )
            Text(
                text = stream.title,
                style = style.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ExpandedStreamCardTags.TITLE),
            )
            if (showPlatform) PlatformBadge(stream.channel.platform)
        },
    ) { measurables, constraints ->
        val gap = HeadlineGap.roundToPx()
        val width = constraints.maxWidth
        val badge = measurables.getOrNull(2)?.measure(Constraints())
        val room = (width - (badge?.let { it.width + gap } ?: 0)).coerceAtLeast(0)
        val nameCap = if (stream.title.isBlank()) room else (room * NAME_SHARE).toInt()
        val name = measurables[0].measure(Constraints(maxWidth = nameCap))
        val title = measurables[1].measure(Constraints(maxWidth = (room - name.width - gap).coerceAtLeast(0)))
        val height = maxOf(name.height, title.height, badge?.height ?: 0)
        layout(width, height) {
            name.placeRelative(0, (height - name.height) / 2)
            title.placeRelative(name.width + gap, (height - title.height) / 2)
            badge?.placeRelative(width - badge.width, (height - badge.height) / 2)
        }
    }
}

/**
 * The live dot and uptime, the viewers and the category. It wraps rather than clips when large text runs out of room.
 * Every piece is silent: the card's own description says them all in one go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(stream: Stream) {
    val locale = LocalConfiguration.current.locales[0]
    val viewers = formatViewerCount(stream.viewers, locale)
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurface
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiveDot(size = 10.dp, describe = false)
            stream.startedAt?.let {
                UptimeText(it, Modifier.testTag(ExpandedStreamCardTags.UPTIME), style = style, color = color)
            }
        }
        WithIcon(R.drawable.ic_visibility) {
            Text(viewers, style = style, color = color, maxLines = 1)
        }
        stream.category?.let { category ->
            WithIcon(R.drawable.ic_sports_esports) {
                Text(category.name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WithIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MetaIconSize),
        )
        text()
    }
}

/** The loading skeleton for [ExpandedStreamCard]. Not interactive. */
@Composable
fun ExpandedStreamCardPlaceholder(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val bone = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ExpandedStreamCardTags.PLACEHOLDER)
                .semantics { contentDescription = loading }
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(RoundedCornerShape(LurkRadius.lg))
                .background(bone),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.size(AvatarSize).clip(CircleShape).background(bone))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(220.dp, 14.dp, bone)
                SkeletonLine(160.dp, 12.dp, bone)
            }
        }
    }
}

@Composable
private fun SkeletonLine(
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

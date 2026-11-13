package io.github.kemurixd.lurk.featureui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.LiveBadge
import io.github.kemurixd.lurk.designsystem.component.LurkAvatar
import io.github.kemurixd.lurk.designsystem.component.PlatformBadge
import io.github.kemurixd.lurk.designsystem.component.StreamThumbnail
import io.github.kemurixd.lurk.designsystem.component.formatViewers
import io.github.kemurixd.lurk.designsystem.theme.LurkRadius
import io.github.kemurixd.lurk.designsystem.theme.PlayerColors
import io.github.kemurixd.lurk.feature.channel.FollowState
import kotlin.time.Instant
import io.github.kemurixd.lurk.designsystem.R as DesignR

private val BannerHeight = 132.dp
private val HeaderHeight = 196.dp
private val AvatarSize = 88.dp
private val AvatarStart = 16.dp
private val AvatarTop = 88.dp
private val IdentityStart = 120.dp
private val IdentityTop = 140.dp
private val BadgeTop = 164.dp
private val BadgeStart = 34.5.dp
private val ButtonIcon = 20.dp
private val PlayButton = 56.dp
private const val THUMBNAIL_RATIO = 16f / 9f

/** Banner, back button, avatar with its live ring and badge, name and status line (Figma Channel header). */
@Composable
internal fun ChannelHeader(
    ref: ChannelRef,
    channel: Channel?,
    now: Instant,
    onBack: () -> Unit,
) {
    val name = channel?.displayName ?: ref.login
    val live = channel?.live
    Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BannerHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = channel?.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp).size(48.dp).testTag(ChannelTestTags.BACK),
        ) {
            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
        }
        val avatarLabel = if (live != null) stringResource(R.string.cd_avatar_live, name) else name
        LurkAvatar(
            imageUrl = channel?.avatarUrl,
            name = name,
            size = AvatarSize,
            liveRing = live != null,
            modifier = Modifier.offset(AvatarStart, AvatarTop).semantics { contentDescription = avatarLabel },
        )
        if (live != null) LiveBadge(Modifier.offset(BadgeStart, BadgeTop))
        Column(
            modifier = Modifier.padding(start = IdentityStart, top = IdentityTop, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                PlatformBadge(ref.platform)
            }
            if (channel != null) {
                Text(
                    text = statusLine(channel, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ChannelTestTags.STATUS),
                )
            }
        }
    }
}

/** "Streaming <category> · 12.4K viewers" with the category in the primary colour, or the offline line. */
@Composable
private fun statusLine(
    channel: Channel,
    now: Instant,
): AnnotatedString {
    val live = channel.live
    if (live == null) {
        val last = channel.lastStream?.startedAt
        return AnnotatedString(
            if (last == null) {
                stringResource(R.string.status_offline)
            } else {
                stringResource(R.string.status_offline_ago, agoText(agoOf(last, now)))
            },
        )
    }
    val viewers = formatViewers(live.viewers)
    val category = live.category?.name
    val text =
        if (category == null) {
            stringResource(R.string.status_live, viewers)
        } else {
            stringResource(R.string.status_streaming, category, viewers)
        }
    val primary = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        val start = category?.let { text.indexOf(it) } ?: -1
        if (category != null && start >= 0) addStyle(SpanStyle(color = primary), start, start + category.length)
    }
}

@Composable
private fun agoText(ago: Ago): String =
    when (ago) {
        Ago.JustNow -> stringResource(R.string.ago_now)
        is Ago.Minutes -> pluralStringResource(R.plurals.ago_minutes, ago.count, ago.count)
        is Ago.Hours -> pluralStringResource(R.plurals.ago_hours, ago.count, ago.count)
        is Ago.Days -> pluralStringResource(R.plurals.ago_days, ago.count, ago.count)
    }

/** The Follow button and the Share button. Signed out, the Follow button becomes "Sign in to follow". */
@Composable
internal fun FollowActions(
    follow: FollowState,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onSignIn: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val modifier = Modifier.weight(1f)
        if (follow == FollowState.Hidden) {
            FilledTonalButton(onClick = onSignIn, modifier = modifier.testTag(ChannelTestTags.SIGN_IN)) {
                ButtonContent(DesignR.drawable.ic_login, stringResource(R.string.sign_in_to_follow))
            }
        } else {
            // While a change runs the button shows where it is heading and can't be pressed again.
            val following = follow == FollowState.Following || (follow as? FollowState.Busy)?.target == true
            val enabled = follow !is FollowState.Busy && follow != FollowState.Checking
            if (following) {
                FilledTonalButton(
                    onClick = onToggle,
                    enabled = enabled,
                    modifier = modifier.testTag(ChannelTestTags.FOLLOW),
                ) {
                    ButtonContent(DesignR.drawable.ic_check, stringResource(R.string.following))
                }
            } else {
                Button(onClick = onToggle, enabled = enabled, modifier = modifier.testTag(ChannelTestTags.FOLLOW)) {
                    ButtonContent(DesignR.drawable.ic_add, stringResource(R.string.follow))
                }
            }
        }
        FilledTonalIconButton(onClick = onShare, modifier = Modifier.size(48.dp).testTag(ChannelTestTags.SHARE)) {
            Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.cd_share))
        }
    }
}

@Composable
private fun ButtonContent(
    icon: Int,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonIcon))
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** The live stream as a 16:9 card with a play button; tapping anywhere on it opens the player. */
@Composable
internal fun LivePreview(
    stream: Stream,
    channelName: String,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.cd_watch_live, channelName)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ChannelTestTags.LIVE_PREVIEW)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StreamThumbnail(
            stream = stream,
            modifier = Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO),
            shape = RoundedCornerShape(LurkRadius.lg),
            overlayInset = 12.dp,
        ) {
            Box(
                Modifier.align(Alignment.Center).size(PlayButton).background(PlayerColors.scrim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_play_arrow),
                    contentDescription = null,
                    tint = PlayerColors.onScrim,
                )
            }
        }
        Text(
            text = stream.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        stream.category?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What the channel last streamed, with a note that the stream shows here when it goes live (Figma Offline card). */
@Composable
internal fun LastStreamCard(channel: Channel) {
    val last = channel.lastStream
    val headline = listOfNotNull(last?.title, last?.category?.name).joinToString(" · ")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ChannelTestTags.LAST_STREAM)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (headline.isNotEmpty()) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.last_stream_footer, channel.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

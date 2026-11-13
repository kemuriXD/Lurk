package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme

private val BadgeSize = 20.dp
private val BadgeCorner = 6.dp
private val TwitchMarkSize = 14.dp
private val KickMarkSize = 12.dp

/** The platform's name as a string resource ("Twitch", "Kick"). The one place this mapping lives. */
@get:StringRes
val Platform.labelRes: Int
    get() =
        when (this) {
            Platform.TWITCH -> R.string.platform_twitch
            Platform.KICK -> R.string.platform_kick
        }

@Composable
internal fun Platform.label(): String = stringResource(labelRes)

/** The small square platform mark (Figma PlatformBadge, Size=S). */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    val colors = LurkTheme.platformColors
    val style =
        when (platform) {
            Platform.TWITCH -> BadgeStyle(colors.twitch, colors.onTwitch, R.drawable.ic_logo_twitch, TwitchMarkSize)
            Platform.KICK -> BadgeStyle(colors.kick, colors.onKick, R.drawable.ic_logo_kick, KickMarkSize)
        }
    Box(
        modifier = modifier.size(BadgeSize).clip(RoundedCornerShape(BadgeCorner)).background(style.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(style.logo),
            contentDescription = platform.label(),
            modifier = Modifier.size(style.markSize),
            tint = style.content,
        )
    }
}

private data class BadgeStyle(
    val background: Color,
    val content: Color,
    @DrawableRes val logo: Int,
    val markSize: Dp,
)

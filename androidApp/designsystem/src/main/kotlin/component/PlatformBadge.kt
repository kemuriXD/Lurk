package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme

private val BadgeSize = 20.dp
private val LogoSize = 18.dp
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

/** The small square platform mark (Figma PlatformBadge, Size=S). It names the platform for screen readers. */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    PlatformMark(platform, BadgeSize, describe = true, modifier = modifier.testTag(PlatformTags.BADGE))
}

/**
 * The platform's logo for before a chip's or a button's text: the same mark as [PlatformBadge], its glyph on the
 * platform's own colour square, so it reads the same on any container, light or dark. The text beside it names the
 * platform, so the logo itself is silent. [size] defaults to the 18 dp of a Material leading icon.
 */
@Composable
fun PlatformLogo(
    platform: Platform,
    modifier: Modifier = Modifier,
    size: Dp = LogoSize,
) {
    PlatformMark(platform, size, describe = false, modifier = modifier.testTag(PlatformTags.logo(platform)))
}

/** [PlatformLogo] at a button's icon size, with the gap a Material button leaves after its leading icon. */
@Composable
fun RowScope.PlatformLogoLeading(
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    PlatformLogo(platform, modifier, size = ButtonDefaults.IconSize)
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
}

@Composable
private fun PlatformMark(
    platform: Platform,
    size: Dp,
    describe: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LurkTheme.platformColors
    val style =
        when (platform) {
            Platform.TWITCH -> BadgeStyle(colors.twitch, colors.onTwitch, R.drawable.ic_logo_twitch, TwitchMarkSize)
            Platform.KICK -> BadgeStyle(colors.kick, colors.onKick, R.drawable.ic_logo_kick, KickMarkSize)
        }
    val scale = size / BadgeSize
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(BadgeCorner * scale)).background(style.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(style.logo),
            contentDescription = if (describe) platform.label() else null,
            modifier = Modifier.size(style.markSize * scale),
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

/** Test tags of the platform marks. */
object PlatformTags {
    /** A platform badge on a row, tile or card. */
    const val BADGE = "platform_badge"

    /** The logo before a chip's or a button's text. */
    fun logo(platform: Platform): String = "platform_logo:${platform.name}"
}

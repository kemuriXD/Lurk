@file:Suppress("MagicNumber")

package io.github.kemurixd.lurk.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Values come from the Figma Design System file (Color, Platform and Highlight collections).

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF126874),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9DEFFE),
        onPrimaryContainer = Color(0xFF011F24),
        inversePrimary = Color(0xFF36D9F0),
        secondary = Color(0xFF3E646B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC1EAF1),
        onSecondaryContainer = Color(0xFF011F24),
        tertiary = Color(0xFF605983),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE6DEFF),
        onTertiaryContainer = Color(0xFF1A173A),
        error = Color(0xFFB12A2F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFDDBD7),
        onErrorContainer = Color(0xFF3D0602),
        surface = Color(0xFFF2FBFC),
        onSurface = Color(0xFF171D1E),
        onSurfaceVariant = Color(0xFF394A4D),
        inverseSurface = Color(0xFF2B3133),
        inverseOnSurface = Color(0xFFEAF2F4),
        outline = Color(0xFF687A7D),
        outlineVariant = Color(0xFFB7CACE),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF2FBFC),
        surfaceDim = Color(0xFFD3DBDD),
        surfaceContainer = Color(0xFFE7EFF1),
        surfaceContainerHigh = Color(0xFFE1EAEB),
        surfaceContainerHighest = Color(0xFFDCE4E5),
        surfaceContainerLow = Color(0xFFEDF5F7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    ).withDerivedRoles()

private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFF36D9F0),
        onPrimary = Color(0xFF04363D),
        primaryContainer = Color(0xFF0E4E57),
        onPrimaryContainer = Color(0xFF9DEFFE),
        inversePrimary = Color(0xFF126874),
        secondary = Color(0xFFA5CDD5),
        onSecondary = Color(0xFF0C353C),
        secondaryContainer = Color(0xFF264C53),
        onSecondaryContainer = Color(0xFFC1EAF1),
        tertiary = Color(0xFFCAC0F0),
        onTertiary = Color(0xFF312B51),
        tertiaryContainer = Color(0xFF48416A),
        onTertiaryContainer = Color(0xFFE6DEFF),
        error = Color(0xFFFEB4AC),
        onError = Color(0xFF650811),
        errorContainer = Color(0xFF8D151E),
        onErrorContainer = Color(0xFFFDDBD7),
        surface = Color(0xFF0D1516),
        onSurface = Color(0xFFDCE4E5),
        onSurfaceVariant = Color(0xFFB7CACE),
        inverseSurface = Color(0xFFDCE4E5),
        inverseOnSurface = Color(0xFF2B3133),
        outline = Color(0xFF829497),
        outlineVariant = Color(0xFF394A4D),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF343A3C),
        surfaceDim = Color(0xFF0D1516),
        surfaceContainer = Color(0xFF1A2122),
        surfaceContainerHigh = Color(0xFF252B2C),
        surfaceContainerHighest = Color(0xFF2F3637),
        surfaceContainerLow = Color(0xFF171D1E),
        surfaceContainerLowest = Color(0xFF061011),
    ).withDerivedRoles()

private val AmoledScheme = DarkScheme.toAmoled()

/** Static schemes: Figma Light, Dark and AMOLED. */
fun lurkColorScheme(
    dark: Boolean,
    amoled: Boolean,
): ColorScheme =
    when {
        !dark -> LightScheme
        amoled -> AmoledScheme
        else -> DarkScheme
    }

/**
 * Applies the Figma AMOLED surfaces to any dark scheme, static or dynamic. This is the one place
 * that holds them: pure black for the background tones, Figma's AMOLED values for the containers.
 */
internal fun ColorScheme.toAmoled(): ColorScheme =
    copy(
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF252B2C),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF061011),
        surfaceContainer = Color(0xFF0D1516),
        surfaceContainerHigh = Color(0xFF171D1E),
        surfaceContainerHighest = Color(0xFF1A2122),
    ).withDerivedRoles()

/** Roles Figma has no variable for, derived from the ones it does. */
private fun ColorScheme.withDerivedRoles(): ColorScheme =
    copy(
        background = surface,
        onBackground = onSurface,
        surfaceVariant = surfaceContainerHighest,
        surfaceTint = primary,
    )

@Immutable
data class PlatformColors(
    val twitch: Color = Color(0xFF9146FF),
    val onTwitch: Color = Color(0xFFFFFFFF),
    val kick: Color = Color(0xFF53FC18),
    val onKick: Color = Color(0xFF000000),
)

@Immutable
data class HighlightColors(
    val sub: Color = Color(0xFFA970FF),
    val announcement: Color = Color(0xFF5C9DFF),
    val firstMessage: Color = Color(0xFF3DBE6F),
    val highlighted: Color = Color(0xFF1FB8C8),
    val gift: Color = Color(0xFFE3B341),
    val mention: Color = Color(0xFFE5484D),
    val cheer: Color = Color(0xFFC79BFF),
    val raid: Color = Color(0xFFF28B54),
)

/** Scrim colours for the player overlay; the same in every mode. */
object PlayerColors {
    val scrim: Color = Color(0x99000000)
    val onScrim: Color = Color(0xFFFFFFFF)
}

/**
 * The red that marks something live: the live dot, its halo and the avatar's live ring. Fixed, not taken from the
 * scheme, so it reads the same in Light, Dark, AMOLED and dynamic colour.
 */
val LiveRed: Color = Color(0xFFE15241)

/** Figma's shadow colour. Material 3 has no ColorScheme slot for it. */
val LurkShadow: Color = Color(0xFF000000)

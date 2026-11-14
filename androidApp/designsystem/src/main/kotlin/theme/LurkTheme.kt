package io.github.kemurixd.lurk.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalSpacing = staticCompositionLocalOf { LurkSpacing() }

private val LocalPlatformColors = staticCompositionLocalOf { PlatformColors() }

private val LocalHighlightColors = staticCompositionLocalOf { HighlightColors() }

/** Accessors for the tokens that Material 3 has no slot for. */
object LurkTheme {
    val spacing: LurkSpacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val platformColors: PlatformColors
        @Composable @ReadOnlyComposable
        get() = LocalPlatformColors.current

    val highlightColors: HighlightColors
        @Composable @ReadOnlyComposable
        get() = LocalHighlightColors.current
}

/**
 * Picks the scheme for the current settings: Wallpaper colours on API 31+ when [dynamicColor] is
 * set, the Figma schemes otherwise. AMOLED pins the surfaces to black over either one.
 */
internal fun resolveColorScheme(
    context: Context,
    dark: Boolean,
    amoled: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return lurkColorScheme(dark, amoled)
    val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (dark && amoled) dynamic.toAmoled() else dynamic
}

@Composable
fun LurkTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val colorScheme = resolveColorScheme(LocalContext.current, dark, amoled, dynamicColor)
    CompositionLocalProvider(
        LocalSpacing provides LurkSpacing(),
        LocalPlatformColors provides PlatformColors(),
        LocalHighlightColors provides HighlightColors(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LurkTypography,
            shapes = LurkShapes,
            content = content,
        )
    }
}

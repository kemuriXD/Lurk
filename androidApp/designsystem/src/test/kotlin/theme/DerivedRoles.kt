package io.github.kemurixd.lurk.designsystem.theme

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertEquals

/** background, onBackground, surfaceVariant and surfaceTint have no Figma variable; they follow these roles. */
fun assertDerivedRoles(
    label: String,
    scheme: ColorScheme,
) {
    assertEquals("$label background", scheme.surface, scheme.background)
    assertEquals("$label onBackground", scheme.onSurface, scheme.onBackground)
    assertEquals("$label surfaceVariant", scheme.surfaceContainerHighest, scheme.surfaceVariant)
    assertEquals("$label surfaceTint", scheme.primary, scheme.surfaceTint)
}

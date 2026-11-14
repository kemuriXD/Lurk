package io.github.kemurixd.lurk.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sizes, line heights, tracking and weights mirror the Figma text styles (a test checks them).
// Figma draws them in Roboto Flex; the app uses the platform Roboto. That the two look alike at
// these sizes is unverified and gets checked in the phone Figma review.
private val Family = FontFamily.Default

private fun style(
    size: Int,
    lineHeight: Int,
    tracking: Float,
    weight: FontWeight,
) = TextStyle(
    fontFamily = Family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontWeight = weight,
)

val LurkTypography =
    Typography(
        displayLarge = style(57, 64, -0.25f, FontWeight.Normal),
        displayMedium = style(45, 52, 0f, FontWeight.Normal),
        displaySmall = style(36, 44, 0f, FontWeight.Normal),
        headlineLarge = style(32, 40, 0f, FontWeight.Normal),
        headlineMedium = style(28, 36, 0f, FontWeight.Normal),
        headlineSmall = style(24, 32, 0f, FontWeight.Normal),
        titleLarge = style(22, 28, 0f, FontWeight.Normal),
        titleMedium = style(16, 24, 0.15f, FontWeight.Medium),
        titleSmall = style(14, 20, 0.1f, FontWeight.Medium),
        bodyLarge = style(16, 24, 0.5f, FontWeight.Normal),
        bodyMedium = style(14, 20, 0.25f, FontWeight.Normal),
        bodySmall = style(12, 16, 0.4f, FontWeight.Normal),
        labelLarge = style(14, 20, 0.1f, FontWeight.Medium),
        labelMedium = style(12, 16, 0.5f, FontWeight.Medium),
        labelSmall = style(11, 16, 0.5f, FontWeight.Medium),
    )

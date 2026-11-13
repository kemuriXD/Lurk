@file:Suppress("MatchingDeclarationName")

package io.github.kemurixd.lurk.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from the Figma Radius collection. */
object LurkRadius {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
    val full = 9999.dp
}

val LurkShapes =
    Shapes(
        extraSmall = RoundedCornerShape(LurkRadius.xs),
        small = RoundedCornerShape(LurkRadius.sm),
        medium = RoundedCornerShape(LurkRadius.md),
        large = RoundedCornerShape(LurkRadius.lg),
        extraLarge = RoundedCornerShape(LurkRadius.xl),
    )

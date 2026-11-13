package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Keeps text off the bottom corners of a container that is clipped to a rounded shape, where the arc would cut the
 * first glyph. [inset] should be at least half the corner radius.
 */
fun Modifier.cornerInset(inset: Dp): Modifier = padding(start = inset, end = inset, bottom = inset)

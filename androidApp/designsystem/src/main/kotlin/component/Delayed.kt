package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** True once [active] has held for [delayMillis]; false the moment it ends, and never at all for a shorter hold. */
@Composable
internal fun rememberDelayed(
    active: Boolean,
    delayMillis: Long,
): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMillis)
            shown = true
        } else {
            shown = false
        }
    }
    return shown && active
}

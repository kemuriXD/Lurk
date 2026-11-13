package io.github.kemurixd.lurk.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LiveRed

private val DotSize = 8.dp
private const val PULSE_MILLIS = 1_400
private const val HALO_GROWTH = 1.2f
private const val HALO_ALPHA = 0.4f

/**
 * The red dot that marks something live. A soft halo grows and fades around it, about every 1.4 s. The halo's
 * animation is read only inside a layer block, so each frame redraws the halo and recomposes nothing, not the dot
 * and not the card or row around it. With animations turned off the dot stands still. [describe] gives it the
 * "Live" description; turn it off where a parent already says it.
 */
@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    size: Dp = DotSize,
    describe: Boolean = true,
) {
    val color = LiveRed
    val phase = if (rememberLivePulses()) rememberPulsePhase() else StillPhase
    val label = if (describe) stringResource(R.string.live) else null
    Box(if (label != null) modifier.semantics { contentDescription = label } else modifier) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val p = phase.value
                    val scale = 1f + p * HALO_GROWTH
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - p) * HALO_ALPHA
                }.background(color, CircleShape),
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

private val StillPhase: State<Float> = mutableFloatStateOf(1f)

@Composable
private fun rememberPulsePhase(): State<Float> =
    rememberInfiniteTransition(label = "live dot").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

/** Whether the dot pulses: not when the system's animation scale is zero (animations off, or reduced motion). */
@Composable
internal fun rememberLivePulses(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

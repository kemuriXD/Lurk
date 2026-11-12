package io.github.kemurixd.lurk.playback

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Live-edge tuning for the player. A `null` offset means "player default". The player layer maps
 * these onto its live configuration and lets the player's own speed control do the catch-up.
 */
data class LiveTuning(
    val targetOffset: Duration?,
    val minOffset: Duration?,
    val maxOffset: Duration?,
    val minSpeed: Float,
    val maxSpeed: Float,
)

object LowLatencyPolicy {
    const val MIN_SPEED = 0.97f
    const val MAX_SPEED = 1.05f

    private val TARGET_PADDING = 1.seconds
    private val MAX_HEADROOM = 6.seconds

    /** First values; they get tuned on a real device. */
    fun tuning(
        lowLatencyOn: Boolean,
        capable: Boolean,
        segment: Duration?,
    ): LiveTuning {
        if (!lowLatencyOn || !capable || segment == null) {
            return LiveTuning(null, null, null, 1f, 1f)
        }
        val target = segment * 2 + TARGET_PADDING
        return LiveTuning(
            targetOffset = target,
            minOffset = segment * 2,
            maxOffset = target + MAX_HEADROOM,
            minSpeed = MIN_SPEED,
            maxSpeed = MAX_SPEED,
        )
    }
}

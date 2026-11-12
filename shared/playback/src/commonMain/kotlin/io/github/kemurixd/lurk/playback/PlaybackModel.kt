package io.github.kemurixd.lurk.playback

import kotlin.time.Duration

/** Ad-related flags from the signed-in viewer's playback token. */
data class TokenFlags(
    val subscriber: Boolean,
    val turbo: Boolean,
    val showAds: Boolean,
) {
    val adFree: Boolean get() = subscriber || turbo || !showAds
}

enum class RollType { PREROLL, MIDROLL, UNKNOWN }

sealed interface AdState {
    data object None : AdState

    /** Stitched ad video is in the stream, so the player may hide it. */
    data class Playing(
        val remaining: Duration,
        val roll: RollType,
        val podLength: Int,
    ) : AdState

    /** A cue-only break with no ad video in the stream. At most a chip is shown. */
    data class Avoided(
        val viaProxy: Boolean,
        val remaining: Duration,
    ) : AdState
}

sealed interface ProxyStatus {
    data object NotUsed : ProxyStatus

    data object SkippedAdFree : ProxyStatus

    data class Active(
        val label: String,
    ) : ProxyStatus

    /** A session that should be proxied is playing direct. */
    data object Unavailable : ProxyStatus
}

/** One entry of a master playlist. */
data class Variant(
    val name: String,
    val url: String,
    val bandwidth: Long,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val audioOnly: Boolean = false,
)

sealed interface QualityPreference {
    data object Auto : QualityPreference

    data class MaxHeight(
        val height: Int,
    ) : QualityPreference

    data object AudioOnly : QualityPreference
}

data class PlaybackPrefs(
    val quality: QualityPreference = QualityPreference.Auto,
    val lowLatency: Boolean = true,
    val proxy: ProxySettings = ProxySettings(),
)

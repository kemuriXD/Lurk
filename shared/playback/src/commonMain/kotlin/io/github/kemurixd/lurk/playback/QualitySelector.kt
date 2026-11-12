package io.github.kemurixd.lurk.playback

object QualitySelector {
    /**
     * The variant to start on, or `null` for Auto (adaptive).
     *
     * - [QualityPreference.Auto]: always `null`.
     * - [QualityPreference.MaxHeight]: the video variant with the greatest height not above the limit; ties go to the
     *   higher frame rate, then the higher bandwidth. A variant with no height counts as too tall. When nothing fits,
     *   the lowest-bandwidth video variant.
     * - [QualityPreference.AudioOnly]: the first audio-only variant, or the lowest-bandwidth video variant when there
     *   is none.
     *
     * `null` also comes back when there is nothing to pick: no video variant for MaxHeight, no variant at all for
     * AudioOnly.
     */
    fun initial(
        variants: List<Variant>,
        pref: QualityPreference,
    ): Variant? {
        val video = variants.filterNot { it.audioOnly }
        return when (pref) {
            QualityPreference.Auto -> {
                null
            }

            QualityPreference.AudioOnly -> {
                variants.firstOrNull { it.audioOnly } ?: video.minByOrNull { it.bandwidth }
            }

            is QualityPreference.MaxHeight -> {
                video
                    .filter { (it.height ?: Int.MAX_VALUE) <= pref.height }
                    .maxWithOrNull(
                        compareBy<Variant> { it.height ?: 0 }
                            .thenBy { it.frameRate ?: 0f }
                            .thenBy { it.bandwidth },
                    ) ?: video.minByOrNull { it.bandwidth }
            }
        }
    }
}

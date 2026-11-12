package io.github.kemurixd.lurk.playback

object QualitySelector {
    /** The variant to start on, or `null` for Auto (adaptive). */
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

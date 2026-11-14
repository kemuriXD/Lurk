package io.github.kemurixd.lurk.designsystem.component

/** Test tags on the error surfaces, so screens can check which one shows. */
object ErrorTags {
    const val STATE = "error_state"
    const val BANNER = "error_banner"
    const val FOOTER = "error_footer"

    /** The small spinner in a Retry button whose retry has run longer than [RETRY_PROGRESS_DELAY_MILLIS]. */
    const val RETRY_PROGRESS = "error_retry_progress"
}

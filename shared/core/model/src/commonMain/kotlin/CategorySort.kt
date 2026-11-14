package io.github.kemurixd.lurk.core.model

/**
 * How a category page orders its live streams. Each platform offers only some of these; its `Capabilities` say which.
 * [RECOMMENDED] is the platform's own pick, personal where the platform knows the user.
 */
enum class CategorySort {
    FEATURED,
    RECOMMENDED,
    VIEWERS_HIGH,
    VIEWERS_LOW,
    RECENT,
}

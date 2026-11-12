package io.github.kemurixd.lurk.platform.api

/** What a platform can do. Feature code checks these instead of the platform id. */
data class Capabilities(
    val follows: Boolean,
    val followMutation: Boolean,
    val chatSend: Boolean,
    val chatHistory: Boolean,
    val cheermotes: Boolean,
)

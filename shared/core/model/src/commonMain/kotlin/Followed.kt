package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** The signed-in user's follows, split into live streams and offline channels. */
@Serializable
data class Followed(
    val live: List<Stream>,
    val offline: List<Channel>,
)

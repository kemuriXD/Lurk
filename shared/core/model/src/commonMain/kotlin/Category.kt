package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** A game or topic a channel streams under. */
@Serializable
data class Category(
    val platform: Platform,
    val id: String,
    val name: String,
    val boxArtUrl: String?,
    val viewers: Long? = null,
)

package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Badge(
    val setId: String,
    val version: String,
    val title: String? = null,
    val imageUrl: String? = null,
)

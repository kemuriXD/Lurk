package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** Channels and categories matching a search. */
@Serializable
data class SearchResults(
    val channels: List<Channel>,
    val categories: List<Category>,
)

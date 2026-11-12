package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** One page of results; [nextCursor] is null on the last page. */
@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
)

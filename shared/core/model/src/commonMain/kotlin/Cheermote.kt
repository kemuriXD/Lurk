package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** One bit-amount tier of a cheermote. */
@Serializable
data class CheermoteTier(
    val minBits: Int,
    val color: String?,
    val imageUrl: String,
)

/** A cheer prefix with its tiers. */
@Serializable
data class Cheermote(
    val prefix: String,
    val tiers: List<CheermoteTier>,
)

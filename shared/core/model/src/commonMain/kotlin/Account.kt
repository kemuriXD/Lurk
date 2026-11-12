package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

/** A signed-in account. [toString] hides the identity so it cannot leak into logs. */
@Serializable
data class Account(
    val platform: Platform,
    val userId: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
) {
    override fun toString(): String = "Account($platform)"
}

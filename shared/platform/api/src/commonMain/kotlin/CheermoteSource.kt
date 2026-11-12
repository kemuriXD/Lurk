package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.model.Cheermote

/** The cheermotes a channel can use. On failure, throw. */
fun interface CheermoteSource {
    suspend fun load(channelId: String): List<Cheermote>
}

package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.model.Badge

/**
 * The chat badges of a channel, keyed `"<setId>/<version>"`. Channel badges override global ones. On failure, throw,
 * like [io.github.kemurixd.lurk.emotes.NativeEmoteSource].
 */
fun interface BadgeSource {
    suspend fun load(channelId: String): Map<String, Badge>
}

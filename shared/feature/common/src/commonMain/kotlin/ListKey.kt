package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Stream

/** The list and lazy-list key of a channel: platform plus login. Never the channel ID, which Kick leaves blank. */
val ChannelRef.key: String get() = "${platform.name}:${login.lowercase()}"

val Stream.key: String get() = channel.key

val Channel.key: String get() = ref.key

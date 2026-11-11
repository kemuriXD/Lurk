package io.github.kemurixd.lurk.core.config

import kotlinx.serialization.Serializable

/** The config schema this build understands. A fetched config with any other version is ignored. */
const val SUPPORTED_SCHEMA = 2

/** Values that change on the platforms' side faster than app releases, served from `config/remote.json`. */
@Serializable
data class RemoteConfig(
    val schemaVersion: Int,
    val twitch: TwitchConfig,
    val kick: KickConfig,
    val emotes: EmoteConfig,
    val killSwitches: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class TwitchConfig(
    val gqlClientId: String,
    val persistedQueries: Map<String, String>,
    val adCueClasses: List<String>,
    val stitchedAdClasses: List<String>,
    val recentMessagesUrl: String,
)

@Serializable
data class KickConfig(
    val apiBase: String,
    val webApiBase: String,
    val realtimeConnectionPath: String,
    val realtimeAuthPath: String,
    val publicChannelTemplates: List<String>,
    val pusherFallback: PusherConfig? = null,
)

@Serializable
data class PusherConfig(
    val key: String,
    val cluster: String,
)

@Serializable
data class EmoteConfig(
    val bttvZeroWidth: List<String>,
)

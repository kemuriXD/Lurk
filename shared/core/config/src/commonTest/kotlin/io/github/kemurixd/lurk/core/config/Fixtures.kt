package io.github.kemurixd.lurk.core.config

/** A config that passes every validation rule. Values are placeholders, not the published ones. */
internal fun validConfig(): RemoteConfig =
    RemoteConfig(
        schemaVersion = SUPPORTED_SCHEMA,
        twitch =
            TwitchConfig(
                gqlClientId = "testclientid",
                persistedQueries = mapOf("SomeQuery" to "a".repeat(64)),
                adCueClasses = listOf("ad-cue"),
                stitchedAdClasses = listOf("stitched-ad"),
                recentMessagesUrl = "https://recent.example.test/{channel}",
            ),
        kick =
            KickConfig(
                apiBase = "https://kick.example.test/api/",
                webApiBase = "https://web.kick.example.test/api/v1/",
                realtimeConnectionPath = "realtime/connection",
                realtimeAuthPath = "realtime/auth/connection",
                publicChannelTemplates = listOf("chatroom_{chatroomId}", "channel.{channelId}"),
            ),
        emotes = EmoteConfig(bttvZeroWidth = listOf("cvMask")),
    )

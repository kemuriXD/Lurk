package io.github.kemurixd.lurk.core.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteConfigDefaultsTest {
    @Test
    fun configV2WithoutNewFieldsParses() {
        val config = Json.decodeFromString<RemoteConfig>(V2_JSON)
        assertEquals("https://gql.twitch.tv/gql", config.twitch.gqlUrl)
        assertEquals("https://usher.ttvnw.net", config.twitch.usherBase)
        assertEquals("wss://irc-ws.chat.twitch.tv:443", config.twitch.ircUrl)
        assertEquals(listOf("pusher", "centrifugo"), config.kick.realtimeAcceptedProviders)
        assertEquals("https://search.kick.com/api/v1/", config.kick.searchApiBase)
    }

    private companion object {
        val V2_JSON =
            """
            {
              "schemaVersion": 2,
              "twitch": {
                "gqlClientId": "id",
                "persistedQueries": {"PlaybackAccessToken": "abc"},
                "adCueClasses": ["twitch-maf-ad"],
                "stitchedAdClasses": ["twitch-stitched-ad"],
                "recentMessagesUrl": "https://recent-messages.robotty.de/api/v2/recent-messages/{channel}"
              },
              "kick": {
                "apiBase": "https://kick.com/api/",
                "webApiBase": "https://web.kick.com/api/v1/",
                "realtimeConnectionPath": "realtime/connection",
                "realtimeAuthPath": "realtime/auth/connection",
                "publicChannelTemplates": ["chatroom_{chatroomId}"]
              },
              "emotes": {"bttvZeroWidth": ["SoSnowy"]},
              "killSwitches": {}
            }
            """.trimIndent()
    }
}

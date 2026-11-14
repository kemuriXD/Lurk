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
        assertEquals("https://kick.com/", config.kick.siteBase)
    }

    @Test
    fun discoveryClientIdsDefaultToTheAndroidThenTheTvAppId() {
        val config = Json.decodeFromString<RemoteConfig>(V2_JSON)
        assertEquals(
            listOf("kd1unb4b3q4t58fwlpcbzcbnm76a8fp", "ue6666qo983tsx6so1t0vnawi233wa"),
            config.twitch.gqlDiscoveryClientIds,
        )
    }

    @Test
    fun discoveryClientIdsAreReadWhenPresent() {
        val json =
            V2_JSON.replace(
                "\"gqlClientId\": \"id\",",
                "\"gqlClientId\": \"id\", \"gqlDiscoveryClientIds\": [\"a\", \"b\", \"c\"],",
            )
        assertEquals(listOf("a", "b", "c"), Json.decodeFromString<RemoteConfig>(json).twitch.gqlDiscoveryClientIds)
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

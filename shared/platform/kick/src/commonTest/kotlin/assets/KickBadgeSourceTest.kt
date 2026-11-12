package io.github.kemurixd.lurk.platform.kick.assets

import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KickBadgeSourceTest {
    @Test
    fun subscriberBadgesKeyed() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("api/channel-live.json"))
                    },
                ).api

            val badges = KickBadgeSource(api).load("a/b")

            assertEquals("/api/v2/channels/a%2Fb", seen.single().url.encodedPath)
            assertEquals(
                mapOf(
                    "subscriber/1" to Badge("subscriber", "1", null, "https://cdn.example/a/1.png"),
                    "subscriber/3" to Badge("subscriber", "3", null, "https://cdn.example/a/3.png"),
                    "subscriber/6" to Badge("subscriber", "6", null, "https://cdn.example/a/6.png"),
                ),
                badges,
            )
        }
}

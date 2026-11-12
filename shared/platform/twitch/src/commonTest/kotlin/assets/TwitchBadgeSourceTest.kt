package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TwitchBadgeSourceTest {
    @Test
    fun channelBadgeOverridesGlobal() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/badges.json")) }
            val badges = TwitchBadgeSource(r.gql).load("1000001")

            assertEquals(
                setOf("broadcaster/1", "moderator/1", "subscriber/0", "subscriber/3"),
                badges.keys,
            )
            assertEquals(
                Badge("subscriber", "0", "Subscriber", "https://cdn.example/badge/channel-subscriber-0.png"),
                badges["subscriber/0"],
            )
            assertEquals(
                Badge("moderator", "1", "Moderator", "https://cdn.example/badge/moderator-1.png"),
                badges["moderator/1"],
            )
        }

    @Test
    fun missingUserKeepsGlobalBadges() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/badges-unknown.json")) }

            assertEquals(
                setOf("broadcaster/1", "subscriber/0"),
                TwitchBadgeSource(r.gql).load("1000001").keys,
            )
        }

    @Test
    fun badItemSkipped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/badges-bad-item.json")) }
            val badges = TwitchBadgeSource(r.gql).load("1000001")

            assertEquals(setOf("broadcaster/1", "moderator/1"), badges.keys)
            assertNull(badges["moderator/1"]?.imageUrl)
        }

    @Test
    fun serverErrorThrows() =
        runTest {
            val r = discoveryRig { reply("{}", HttpStatusCode.InternalServerError) }

            val e = assertFailsWith<LurkException> { TwitchBadgeSource(r.gql).load("1000001") }
            assertEquals(LurkError.Http(500), e.error)
        }
}

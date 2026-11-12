package io.github.kemurixd.lurk.platform.twitch.account

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun HttpRequestData.variables(): JsonObject =
    Json
        .parseToJsonElement((body as TextContent).text)
        .jsonArray
        .single()
        .jsonObject["variables"]!!
        .jsonObject

class FollowsQueryTest {
    private fun Outcome<Followed>.value(): Followed {
        assertIs<Outcome.Success<Followed>>(this)
        return value
    }

    @Test
    fun followedSplitsLiveAndOffline() =
        runTest {
            val r = discoveryRig(signedIn = true) { reply(fixture("gql/follows.json")) }
            val followed = r.gql.followed(r.sessions).value()

            assertEquals(listOf("streamer3", "streamer1"), followed.live.map { it.channel.login })
            assertEquals(listOf(5000L, 100L), followed.live.map { it.viewers })
            assertEquals(listOf("streamer2"), followed.offline.map { it.ref.login })
            val offline = followed.offline.single()
            assertEquals("1000002", offline.id)
            assertEquals("streamer2", offline.chatId)
            assertEquals("Streamer Two", offline.displayName)
            assertEquals("https://cdn.example/a/2.png", offline.avatarUrl)
            assertNull(offline.live)
            assertEquals("OAuth rig-token", r.seen[0].headers["Authorization"])
        }

    @Test
    fun followedPagesUntilEnd() =
        runTest {
            val r =
                discoveryRig(signedIn = true) {
                    val after = it.variables()["after"]?.jsonPrimitive?.content
                    reply(fixture(if (after == null) "gql/follows-page1.json" else "gql/follows-page2.json"))
                }
            val followed = r.gql.followed(r.sessions).value()

            assertEquals(2, r.seen.size)
            assertEquals(
                "cursor-2",
                r
                    .operation(1)["variables"]!!
                    .jsonObject["after"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(
                100,
                r
                    .operation(0)["variables"]!!
                    .jsonObject["first"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals(listOf("streamer3", "streamer1"), followed.live.map { it.channel.login })
            assertEquals(listOf("streamer2"), followed.offline.map { it.ref.login })
        }

    @Test
    fun followedStopsAtTwentyPages() =
        runTest {
            val r = discoveryRig(signedIn = true) { reply(fixture("gql/follows-page1.json")) }
            val followed = r.gql.followed(r.sessions)

            assertIs<Outcome.Success<Followed>>(followed)
            assertEquals(20, r.seen.size)
        }

    @Test
    fun signedOutFollowedIsNotSignedIn() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/follows.json")) }

            assertEquals(Outcome.Failure(LurkError.NotSignedIn), r.gql.followed(r.sessions))
            assertFalse(r.seen.isNotEmpty())
        }
}

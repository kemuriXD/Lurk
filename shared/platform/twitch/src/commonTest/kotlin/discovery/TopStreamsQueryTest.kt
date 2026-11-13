package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.twitch.fixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TopStreamsQueryTest {
    private val categoryOne =
        Category(Platform.TWITCH, "100001", "Category One", "https://cdn.example/b/1-285x380.jpg")

    private val first =
        Stream(
            channel = ChannelRef(Platform.TWITCH, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Stream title 1",
            category = categoryOne,
            viewers = 12345,
            startedAt = Instant.parse("2026-01-01T10:00:00Z"),
            thumbnailUrl = "https://cdn.example/t/streamer1-440x248.jpg",
            avatarUrl = "https://cdn.example/a/1.png",
        )

    private fun Outcome<Page<Stream>>.page(): Page<Stream> {
        assertIs<Outcome.Success<Page<Stream>>>(this)
        return value
    }

    @Test
    fun mapsTheFixture() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            val page = r.gql.topLive(null).page()

            assertEquals(2, page.items.size)
            assertEquals(first, page.items[0])
        }

    @Test
    fun brokenStreamItemSkipped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            val logins =
                r.gql
                    .topLive(null)
                    .page()
                    .items
                    .map { it.channel.login }

            assertEquals(listOf("streamer1", "streamer3"), logins)
        }

    @Test
    fun nullGameIsNullCategory() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            val third =
                r.gql
                    .topLive(null)
                    .page()
                    .items[1]

            assertNull(third.category)
            assertEquals(100, third.viewers)
        }

    @Test
    fun missingViewersIsZero() =
        runTest {
            val body = fixture("gql/top-streams.json").replace("\"viewersCount\":12345,", "")
            val r = discoveryRig { reply(body) }

            assertEquals(
                0,
                r.gql
                    .topLive(null)
                    .page()
                    .items[0]
                    .viewers,
            )
        }

    @Test
    fun cursorIsTheLastEdgeWhenThereIsMore() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }

            assertEquals(
                "cursor-3",
                r.gql
                    .topLive(null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun noCursorOnTheLastPage() =
        runTest {
            val body = fixture("gql/top-streams.json").replace("\"hasNextPage\":true", "\"hasNextPage\":false")
            val r = discoveryRig { reply(body) }

            assertNull(
                r.gql
                    .topLive(null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun asksForThirtyPerPageTheMostTopLiveAllows() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            r.gql.topLive("cursor-9")
            r.gql.topLive(null)

            val variables = r.operation(0)["variables"]!!.jsonObject
            assertEquals(30, variables["first"]!!.jsonPrimitive.int)
            assertEquals("cursor-9", variables["after"]!!.jsonPrimitive.content)
            assertFalse("after" in r.operation(1)["variables"]!!.jsonObject)
            assertTrue("streams(first: \$first, after: \$after)" in r.operation(0)["query"]!!.jsonPrimitive.content)
            assertNull(r.seen[0].headers["Authorization"])
        }

    @Test
    fun signedInStillSendsTheAndroidIdAndNoToken() =
        runTest {
            val r = discoveryRig(signedIn = true) { reply(fixture("gql/top-streams.json")) }
            r.gql.topLive(null).page()

            assertEquals("kd1unb4b3q4t58fwlpcbzcbnm76a8fp", r.seen.single().headers["Client-Id"])
            assertNull(r.seen.single().headers["Authorization"])
        }

    @Test
    fun anIntegrityFailureOnTheAndroidIdFallsBackToTheTvId() =
        runTest {
            val r =
                discoveryRig(signedIn = true) { request ->
                    if (request.headers["Client-Id"] == "kd1unb4b3q4t58fwlpcbzcbnm76a8fp") {
                        reply(fixture("gql/error-integrity.json"))
                    } else {
                        reply(fixture("gql/top-streams.json"))
                    }
                }
            val page = r.gql.topLive(null).page()

            assertEquals(
                listOf("kd1unb4b3q4t58fwlpcbzcbnm76a8fp", "ue6666qo983tsx6so1t0vnawi233wa"),
                r.seen.map {
                    it.headers["Client-Id"]
                },
            )
            assertTrue(r.seen.all { it.headers["Authorization"] == null })
            assertEquals(2, page.items.size)
        }

    @Test
    fun bothIdsFailingIsIntegrityRequired() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/error-integrity.json")) }

            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), r.gql.topLive(null))
        }
}

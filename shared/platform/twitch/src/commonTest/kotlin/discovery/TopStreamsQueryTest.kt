package io.github.kemurixd.lurk.platform.twitch.discovery

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
            val page = r.gql.topLive(r.sessions, null).page()

            assertEquals(2, page.items.size)
            assertEquals(first, page.items[0])
        }

    @Test
    fun brokenStreamItemSkipped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            val logins =
                r.gql
                    .topLive(r.sessions, null)
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
                    .topLive(r.sessions, null)
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
                    .topLive(r.sessions, null)
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
                    .topLive(r.sessions, null)
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
                    .topLive(r.sessions, null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun asksForThirtyAfterTheCursor() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/top-streams.json")) }
            r.gql.topLive(r.sessions, "cursor-9")
            r.gql.topLive(r.sessions, null)

            val variables = r.operation(0)["variables"]!!.jsonObject
            assertEquals(30, variables["first"]!!.jsonPrimitive.int)
            assertEquals("cursor-9", variables["after"]!!.jsonPrimitive.content)
            assertFalse("after" in r.operation(1)["variables"]!!.jsonObject)
            assertTrue("streams(first: \$first, after: \$after)" in r.operation(0)["query"]!!.jsonPrimitive.content)
            assertNull(r.seen[0].headers["Authorization"])
        }
}

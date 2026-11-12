package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.twitch.fixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GameStreamsQueryTest {
    private fun Outcome<Page<Stream>>.page(): Page<Stream> {
        assertIs<Outcome.Success<Page<Stream>>>(this)
        return value
    }

    @Test
    fun mapsTheFixture() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/game-streams.json")) }
            val page = r.gql.liveInCategory(r.sessions, "100001", null).page()

            assertEquals(listOf("streamer1", "streamer2", "streamer3"), page.items.map { it.channel.login })
            assertEquals(listOf(12345L, 6789L, 100L), page.items.map { it.viewers })
            assertEquals("Category One", page.items[0].category?.name)
            assertEquals("https://cdn.example/t/streamer2-440x248.jpg", page.items[1].thumbnailUrl)
        }

    @Test
    fun lastPageHasNoCursor() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/game-streams.json")) }

            assertNull(
                r.gql
                    .liveInCategory(r.sessions, "100001", null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun cursorIsTheLastEdgeWhenThereIsMore() =
        runTest {
            val body = fixture("gql/game-streams.json").replace("\"hasNextPage\":false", "\"hasNextPage\":true")
            val r = discoveryRig { reply(body) }

            assertEquals(
                "cursor-3",
                r.gql
                    .liveInCategory(r.sessions, "100001", null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun brokenStreamItemSkipped() =
        runTest {
            val body = fixture("gql/game-streams.json").replace("\"login\":\"streamer2\",", "")
            val r = discoveryRig { reply(body) }
            val logins =
                r.gql
                    .liveInCategory(r.sessions, "100001", null)
                    .page()
                    .items
                    .map { it.channel.login }

            assertEquals(listOf("streamer1", "streamer3"), logins)
        }

    @Test
    fun sendsTheIdAndCursor() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/game-streams.json")) }
            r.gql.liveInCategory(r.sessions, "100001", "cursor-9")

            val variables = r.operation()["variables"]!!.jsonObject
            assertEquals("100001", variables["id"]!!.jsonPrimitive.content)
            assertEquals(30, variables["first"]!!.jsonPrimitive.int)
            assertEquals("cursor-9", variables["after"]!!.jsonPrimitive.content)
        }

    @Test
    fun unknownCategoryIsNotFound() =
        runTest {
            val r = discoveryRig { reply("""[{"data":{"game":null}}]""") }

            assertEquals(Outcome.Failure(LurkError.NotFound), r.gql.liveInCategory(r.sessions, "0", null))
        }
}

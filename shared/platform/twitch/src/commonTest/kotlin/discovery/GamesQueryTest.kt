package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.twitch.fixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamesQueryTest {
    private fun Outcome<Page<Category>>.page(): Page<Category> {
        assertIs<Outcome.Success<Page<Category>>>(this)
        return value
    }

    @Test
    fun mapsTheFixtureInTheOrderTwitchGivesIt() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/games.json")) }
            val page = r.gql.categories(r.sessions, null).page()

            assertEquals(3, page.items.size)
            assertEquals(
                Category(Platform.TWITCH, "100001", "Category One", "https://cdn.example/b/1-285x380.jpg", 30000),
                page.items[0],
            )
            assertEquals(listOf(30000L, 20000L, 10000L), page.items.map { it.viewers })
        }

    @Test
    fun gameWithoutANameIsSkipped() =
        runTest {
            val body = fixture("gql/games.json").replace("\"name\":\"Category Two\",", "")
            val r = discoveryRig { reply(body) }

            assertEquals(
                listOf("100001", "100003"),
                r.gql
                    .categories(r.sessions, null)
                    .page()
                    .items
                    .map { it.id },
            )
        }

    @Test
    fun cursorIsTheLastEdgeWhenThereIsMore() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/games.json")) }

            assertEquals(
                "cursor-3",
                r.gql
                    .categories(r.sessions, null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun noCursorOnTheLastPage() =
        runTest {
            val body = fixture("gql/games.json").replace("\"hasNextPage\":true", "\"hasNextPage\":false")
            val r = discoveryRig { reply(body) }

            assertNull(
                r.gql
                    .categories(r.sessions, null)
                    .page()
                    .nextCursor,
            )
        }

    @Test
    fun asksForThirtyAfterTheCursor() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/games.json")) }
            r.gql.categories(r.sessions, "cursor-9")

            val variables = r.operation()["variables"]!!.jsonObject
            assertEquals(30, variables["first"]!!.jsonPrimitive.int)
            assertEquals("cursor-9", variables["after"]!!.jsonPrimitive.content)
            assertTrue("games(first: \$first, after: \$after)" in r.operation()["query"]!!.jsonPrimitive.content)
        }
}

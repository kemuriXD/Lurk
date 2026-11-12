package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.platform.twitch.fixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {
    private fun Outcome<SearchResults>.results(): SearchResults {
        assertIs<Outcome.Success<SearchResults>>(this)
        return value
    }

    @Test
    fun mapsChannelsAndCategories() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/search.json")) }
            val results = r.gql.search(r.sessions, "chess").results()

            val live = results.channels[0]
            assertEquals(ChannelRef(Platform.TWITCH, "streamer2"), live.ref)
            assertEquals("1000002", live.id)
            assertEquals("streamer2", live.chatId)
            assertEquals("Streamer Two", live.displayName)
            assertEquals("https://cdn.example/a/2.png", live.avatarUrl)
            assertEquals("Bio 2", live.bio)
            assertEquals(5001L, live.followers)
            assertEquals(11345L, live.live?.viewers)
            assertEquals(
                listOf(
                    Category(Platform.TWITCH, "100001", "Category One", "https://cdn.example/b/1-285x380.jpg", 30000),
                    Category(Platform.TWITCH, "100002", "Category Two", "https://cdn.example/b/2-285x380.jpg", 20000),
                ),
                results.categories,
            )
        }

    @Test
    fun searchPutsLiveFirst() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/search.json")) }
            val channels: List<Channel> =
                r.gql
                    .search(r.sessions, "chess")
                    .results()
                    .channels

            assertEquals(listOf("streamer2", "streamer3", "streamer1"), channels.map { it.ref.login })
            assertNotNull(channels[0].live)
            assertNotNull(channels[1].live)
            assertNull(channels[2].live)
        }

    @Test
    fun brokenChannelItemSkipped() =
        runTest {
            val body = fixture("gql/search.json").replace("\"login\":\"streamer3\",", "")
            val r = discoveryRig { reply(body) }

            assertEquals(
                listOf("streamer2", "streamer1"),
                r.gql
                    .search(r.sessions, "chess")
                    .results()
                    .channels
                    .map { it.ref.login },
            )
        }

    @Test
    fun sendsTheTermToTheWebSearch() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/search.json")) }
            r.gql.search(r.sessions, "chess")

            val op = r.operation()
            assertEquals("chess", op["variables"]!!.jsonObject["query"]!!.jsonPrimitive.content)
            val query = op["query"]!!.jsonPrimitive.content
            assertTrue("searchFor(userQuery: \$query, platform: \"web\")" in query, query)
        }
}

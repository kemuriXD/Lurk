package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.platform.kick.queryOf
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class KickSearchTest {
    private val streamerTwoLive =
        Stream(
            channel = ChannelRef(Platform.KICK, "streamer2"),
            channelId = "1000002",
            displayName = "Streamer Two",
            title = "Stream title 2",
            category = Category(Platform.KICK, "15", "Category 1", null, null),
            viewers = 2000,
            startedAt = Instant.parse("2026-01-01T11:00:00Z"),
            thumbnailUrl = "https://cdn.example/t/2.webp",
            avatarUrl = "https://cdn.example/a/2.png",
        )

    private fun Outcome<SearchResults>.results(): SearchResults {
        assertIs<Outcome.Success<SearchResults>>(this)
        return value
    }

    @Test
    fun searchMapsChannelsAndCategories() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/search.json"))
                    },
                ).api
            val results = api.search("some words").results()

            val request = seen.single()
            assertEquals("search.kick.example", request.url.host)
            assertEquals("/api/v1/search/enriched", request.url.encodedPath)
            assertEquals("some words", request.queryOf("query"))
            assertEquals(
                listOf(
                    Category(Platform.KICK, "15", "Category 1", "https://cdn.example/b/1.webp", 5000),
                    Category(Platform.KICK, "28", "Category 2", null, 0),
                ),
                results.categories,
            )
            val live = results.channels[0]
            assertEquals(
                Channel(
                    ref = ChannelRef(Platform.KICK, "streamer2"),
                    id = "1000002",
                    chatId = "",
                    displayName = "Streamer Two",
                    avatarUrl = "https://cdn.example/a/2.png",
                    bannerUrl = null,
                    bio = null,
                    followers = 2000,
                    live = streamerTwoLive,
                    lastStream = null,
                ),
                live,
            )
        }

    @Test
    fun liveChannelsComeFirstInKicksOrder() =
        runTest {
            val api = Rig(mockEngine { json(fixture("discovery/search.json")) }).api
            val channels = api.search("x").results().channels

            assertEquals(listOf("streamer2", "streamer3", "streamer1"), channels.map { it.ref.login })
            assertNull(channels[1].live)
            assertNull(channels[2].live)
            assertEquals(30L, channels[1].followers)
            assertNull(channels[1].avatarUrl)
        }

    @Test
    fun searchSendsNoToken() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/search.json"))
                    },
                ).api
            api.search("x")

            assertNull(seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun brokenChannelItemSkipped() =
        runTest {
            val body = fixture("discovery/search.json").replace("\"slug\": \"streamer1\"", "\"slug\": 7")
            val api = Rig(mockEngine { json(body) }).api

            assertEquals(
                listOf("streamer2", "streamer3"),
                api
                    .search("x")
                    .results()
                    .channels
                    .map { it.ref.login },
            )
        }

    @Test
    fun replyWithoutDataIsSchemaChanged() =
        runTest {
            val api = Rig(mockEngine { json("{}") }).api

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), api.search("x"))
        }
}

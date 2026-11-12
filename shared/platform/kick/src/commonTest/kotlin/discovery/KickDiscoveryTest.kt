package io.github.kemurixd.lurk.platform.kick.discovery

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.RIG_TOKEN
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.platform.kick.queryOf
import io.github.kemurixd.lurk.platform.kick.rigSessions
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class KickDiscoveryTest {
    private val categoryOne = Category(Platform.KICK, "15", "Category 1", null, null)

    private val first =
        Stream(
            channel = ChannelRef(Platform.KICK, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Stream title 1",
            category = categoryOne,
            viewers = 12345,
            startedAt = Instant.parse("2026-01-01T10:00:00Z"),
            thumbnailUrl = "https://cdn.example/t/1.webp",
            avatarUrl = "https://cdn.example/a/1.png",
        )

    private fun <T> Outcome<Page<T>>.page(): Page<T> {
        assertIs<Outcome.Success<Page<T>>>(this)
        return value
    }

    @Test
    fun topLiveMapsTheFixture() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/top-live.json"))
                    },
                ).api
            val page = api.topLive(rigSessions(), null).page()

            assertEquals(first, page.items[0])
            assertEquals("cursor-2", page.nextCursor)
            val request = seen.single()
            assertEquals("web.kick.example", request.url.host)
            assertEquals("/api/v1/livestreams", request.url.encodedPath)
            assertEquals("24", request.queryOf("limit"))
            assertEquals("viewer_count_desc", request.queryOf("sort"))
            assertNull(request.queryOf("after"))
            assertNull(request.queryOf("category_id"))
        }

    @Test
    fun brokenStreamItemSkipped() =
        runTest {
            val api = Rig(mockEngine { json(fixture("discovery/top-live.json")) }).api
            val page = api.topLive(rigSessions(), null).page()

            assertEquals(listOf("streamer1", "streamer3"), page.items.map { it.channel.login })
        }

    @Test
    fun missingFieldsFallBack() =
        runTest {
            val api = Rig(mockEngine { json(fixture("discovery/top-live.json")) }).api
            val third = api.topLive(rigSessions(), null).page().items[1]

            assertEquals("streamer3", third.displayName)
            assertEquals("1000003", third.channelId)
            assertNull(third.category)
            assertNull(third.startedAt)
            assertNull(third.thumbnailUrl)
            assertNull(third.avatarUrl)
            assertEquals(100L, third.viewers)
        }

    @Test
    fun topLiveSendsTheCursorAsAfter() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/top-live-last.json"))
                    },
                ).api
            val page = api.topLive(rigSessions(), "cursor-2").page()

            assertEquals("cursor-2", seen.single().queryOf("after"))
            assertEquals(listOf("streamer4"), page.items.map { it.channel.login })
            assertNull(page.nextCursor)
        }

    @Test
    fun categoryLiveFiltersByCategory() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/top-live.json"))
                    },
                ).api
            val page = api.categoryLive(rigSessions(), "15", "cursor-2").page()

            val request = seen.single()
            assertEquals("/api/v1/livestreams", request.url.encodedPath)
            assertEquals("15", request.queryOf("category_id"))
            assertEquals("cursor-2", request.queryOf("after"))
            assertEquals("viewer_count_desc", request.queryOf("sort"))
            assertEquals(first, page.items[0])
        }

    @Test
    fun blankCategoryIsNotFoundWithoutARequest() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/top-live.json"))
                    },
                ).api

            assertEquals(Outcome.Failure(LurkError.NotFound), api.categoryLive(rigSessions(), "", null))
            assertEquals(Outcome.Failure(LurkError.NotFound), api.categoryLive(rigSessions(), "  ", null))
            assertEquals(emptyList(), seen)
        }

    @Test
    fun channelPathEscapesTheSlug() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("api/channel-live.json"))
                    },
                ).api
            api.channel(rigSessions(), ChannelRef(Platform.KICK, "a/b?c#d"))

            assertEquals("/api/v2/channels/a%2Fb%3Fc%23d", seen.single().url.encodedPath)
        }

    @Test
    fun categoriesPageCursor() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(
                            fixture(
                                if (it.queryOf("page") ==
                                    "2"
                                ) {
                                    "discovery/categories-page2.json"
                                } else {
                                    "discovery/categories-page1.json"
                                },
                            ),
                        )
                    },
                ).api
            val sessions = rigSessions()
            val first = api.categories(sessions, null).page()
            val second = api.categories(sessions, first.nextCursor).page()

            assertEquals("kick.example", seen[0].url.host)
            assertEquals("/api/v1/subcategories", seen[0].url.encodedPath)
            assertEquals("1", seen[0].queryOf("page"))
            assertEquals("32", seen[0].queryOf("limit"))
            assertEquals("2", first.nextCursor)
            assertEquals("2", seen[1].queryOf("page"))
            assertNull(second.nextCursor)
            assertEquals(
                listOf(
                    Category(Platform.KICK, "15", "Category 1", "https://cdn.example/b/1.webp", 5000),
                    Category(Platform.KICK, "28", "Category 2", "https://cdn.example/b/2.webp", 300),
                    Category(Platform.KICK, "77", "Category 3", null, 0),
                ),
                first.items,
            )
            assertEquals(
                listOf(Category(Platform.KICK, "90", "Category 4", "https://cdn.example/b/4.webp", 10)),
                second.items,
            )
        }

    @Test
    fun channelMapsThroughTheSharedReader() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("api/channel-live.json"))
                    },
                ).api
            val result = api.channel(rigSessions(), ChannelRef(Platform.KICK, "streamer1"))

            assertIs<Outcome.Success<*>>(result)
            val channel = (result as Outcome.Success).value
            assertEquals("1000001", channel.id)
            assertEquals("2000001", channel.chatId)
            assertEquals("streamer1", channel.ref.login)
            assertEquals("/api/v2/channels/streamer1", seen.single().url.encodedPath)
        }

    @Test
    fun unknownChannelIsNotFound() =
        runTest {
            val api = Rig(mockEngine { json("""{"message":"Channel not found."}""", HttpStatusCode.NotFound) }).api

            assertEquals(
                Outcome.Failure(LurkError.NotFound),
                api.channel(rigSessions(), ChannelRef(Platform.KICK, "nobody")),
            )
        }

    @Test
    fun channelWithoutChatroomIsSchemaChanged() =
        runTest {
            val body = fixture("api/channel-live.json").replace("\"chatroom\"", "\"chatroom_gone\"")
            val api = Rig(mockEngine { json(body) }).api

            assertEquals(
                Outcome.Failure(LurkError.SchemaChanged),
                api.channel(rigSessions(), ChannelRef(Platform.KICK, "streamer1")),
            )
        }

    @Test
    fun signedInRequestsCarryTheToken() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("discovery/top-live.json"))
                    },
                ).api
            api.topLive(rigSessions(signedIn = true), null)
            api.topLive(rigSessions(), null)

            assertEquals("Bearer $RIG_TOKEN", seen[0].headers[HttpHeaders.Authorization])
            assertNull(seen[1].headers[HttpHeaders.Authorization])
        }
}

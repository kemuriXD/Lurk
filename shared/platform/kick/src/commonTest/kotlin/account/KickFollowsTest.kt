package io.github.kemurixd.lurk.platform.kick.account

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.platform.kick.RIG_TOKEN
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.channel.LiveDetailsMemo
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.platform.kick.queryOf
import io.github.kemurixd.lurk.platform.kick.rigSessions
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class KickFollowsTest {
    private val ref = ChannelRef(Platform.KICK, "streamer1")

    private fun Outcome<Followed>.value(): Followed {
        assertIs<Outcome.Success<Followed>>(this)
        return value
    }

    private fun offline(
        slug: String,
        name: String,
        avatar: String?,
    ) = Channel(
        ref = ChannelRef(Platform.KICK, slug),
        id = "",
        chatId = "",
        displayName = name,
        avatarUrl = avatar,
        bannerUrl = null,
        bio = null,
        followers = null,
        live = null,
        lastStream = null,
    )

    @Test
    fun followedPages() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(
                            fixture(
                                if (it.queryOf("cursor") ==
                                    null
                                ) {
                                    "account/followed-page1.json"
                                } else {
                                    "account/followed-page2.json"
                                },
                            ),
                        )
                    },
                ).api
            val followed = api.followed(rigSessions(signedIn = true)).value()
            seen.removeAll { it.url.encodedPath != "/api/v2/channels/followed" }

            assertEquals(2, seen.size)
            assertEquals("/api/v2/channels/followed", seen[0].url.encodedPath)
            assertEquals(null, seen[0].queryOf("cursor"))
            assertEquals("3", seen[1].queryOf("cursor"))
            assertTrue(seen.all { it.headers[HttpHeaders.Authorization] == "Bearer $RIG_TOKEN" })
            assertEquals(listOf("streamer3", "streamer2"), followed.live.map { it.channel.login })
            assertEquals(listOf("streamer4", "streamer5", "streamer1"), followed.offline.map { it.ref.login })
        }

    @Test
    fun followedMapsLiveAndOfflineEntries() =
        runTest {
            val api =
                Rig(
                    mockEngine {
                        json(
                            fixture(
                                if (it.queryOf("cursor") ==
                                    null
                                ) {
                                    "account/followed-page1.json"
                                } else {
                                    "account/followed-page2.json"
                                },
                            ),
                        )
                    },
                ).api
            val followed = api.followed(rigSessions(signedIn = true)).value()

            assertEquals(
                Stream(
                    channel = ChannelRef(Platform.KICK, "streamer2"),
                    channelId = "",
                    displayName = "Streamer Two",
                    title = "Stream title 2",
                    category = null,
                    viewers = 100,
                    startedAt = null,
                    thumbnailUrl = null,
                    avatarUrl = "https://cdn.example/a/2.png",
                ),
                followed.live[1],
            )
            assertEquals(
                Stream(
                    channel = ChannelRef(Platform.KICK, "streamer3"),
                    channelId = "",
                    displayName = "Streamer Three",
                    title = "",
                    category = null,
                    viewers = 5000,
                    startedAt = null,
                    thumbnailUrl = null,
                    avatarUrl = null,
                ),
                followed.live[0],
            )
            assertEquals(
                listOf(
                    offline("streamer4", "alpha Four", "https://cdn.example/a/4.png"),
                    offline("streamer5", "Bravo Five", "https://cdn.example/a/5.png"),
                    offline("streamer1", "Streamer One", "https://cdn.example/a/1.png"),
                ),
                followed.offline,
            )
        }

    /** Serves the two follows pages, and [livestream] for each live channel's livestream lookup. */
    private suspend fun MockRequestHandleScope.follows(
        request: HttpRequestData,
        livestream: suspend MockRequestHandleScope.(slug: String) -> HttpResponseData,
    ): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/livestream") -> livestream(path.removeSuffix("/livestream").substringAfterLast('/'))
            request.queryOf("cursor") == null -> json(fixture("account/followed-page1.json"))
            else -> json(fixture("account/followed-page2.json"))
        }
    }

    @Test
    fun liveFollowedStreamsGetTheirThumbnailCategoryAndStartFromTheLivestream() =
        runTest {
            val api = Rig(mockEngine { follows(it) { json(fixture("api/livestream-live.json")) } }).api
            val followed = api.followed(rigSessions(signedIn = true)).value()

            assertEquals(2, followed.live.size)
            for (stream in followed.live) {
                assertEquals("https://cdn.example/t/9.webp", stream.thumbnailUrl)
                assertEquals("Category 1", stream.category?.name)
                assertEquals(Category(Platform.KICK, "15", "Category 1", null), stream.category)
                assertEquals(Instant.parse("2026-01-01T09:00:00Z"), stream.startedAt)
            }
        }

    @Test
    fun aFailedLivestreamLookupLeavesThatCardBareButKeepsTheList() =
        runTest {
            val api =
                Rig(
                    mockEngine {
                        follows(it) { slug ->
                            when (slug) {
                                "streamer2" -> json("{}", HttpStatusCode.InternalServerError)
                                else -> json(fixture("api/livestream-live.json"))
                            }
                        }
                    },
                ).api
            val followed = api.followed(rigSessions(signedIn = true)).value()

            val byLogin = followed.live.associateBy { it.channel.login }
            assertNull(byLogin.getValue("streamer2").thumbnailUrl)
            assertNull(byLogin.getValue("streamer2").category)
            assertEquals("https://cdn.example/t/9.webp", byLogin.getValue("streamer3").thumbnailUrl)
            assertEquals(3, followed.offline.size)
        }

    @Test
    fun aStreamThatEndedBeforeTheLookupKeepsNoThumbnail() =
        runTest {
            val api = Rig(mockEngine { follows(it) { json(fixture("api/livestream-offline.json")) } }).api
            val followed = api.followed(rigSessions(signedIn = true)).value()

            assertEquals(2, followed.live.size)
            assertTrue(followed.live.all { it.thumbnailUrl == null && it.category == null })
        }

    @Test
    fun onlyLiveFollowsCostALivestreamRequest() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        follows(it) { json(fixture("api/livestream-live.json")) }
                    },
                ).api
            api.followed(rigSessions(signedIn = true)).value()

            val lookups = seen.map { it.url.encodedPath }.filter { it.endsWith("/livestream") }
            assertEquals(
                listOf("/api/v2/channels/streamer2/livestream", "/api/v2/channels/streamer3/livestream"),
                lookups.sorted(),
            )
        }

    @Test
    fun livestreamLookupsRunAtMostFourAtATime() =
        runTest {
            val many =
                (1..10).joinToString(",", prefix = """{"channels":[""", postfix = "]}") {
                    """{"is_live":true,"channel_slug":"live$it","viewer_count":$it,"user_username":"Live $it"}"""
                }
            var running = 0
            var peak = 0
            val api =
                Rig(
                    mockEngine {
                        if (it.url.encodedPath.endsWith("/livestream")) {
                            running++
                            peak = maxOf(peak, running)
                            delay(100)
                            running--
                            json(fixture("api/livestream-live.json"))
                        } else {
                            json(many)
                        }
                    },
                ).api
            val followed = api.followed(rigSessions(signedIn = true)).value()

            assertEquals(10, followed.live.count { it.thumbnailUrl != null })
            assertEquals(4, peak)
        }

    @Test
    fun aLookupThatNeverAnswersTimesOutAndLeavesOnlyThatCardBare() =
        runTest {
            val api =
                Rig(
                    mockEngine {
                        follows(it) { slug ->
                            if (slug == "streamer2") delay(60_000)
                            json(fixture("api/livestream-live.json"))
                        }
                    },
                ).api
            val started = testScheduler.currentTime
            val followed = api.followed(rigSessions(signedIn = true)).value()

            assertTrue(testScheduler.currentTime - started <= 5_000, "took ${testScheduler.currentTime - started} ms")
            val byLogin = followed.live.associateBy { it.channel.login }
            assertNull(byLogin.getValue("streamer2").thumbnailUrl)
            assertEquals("https://cdn.example/t/9.webp", byLogin.getValue("streamer3").thumbnailUrl)
        }

    @Test
    fun theMemoStopsARefetchWithinItsWindow() =
        runTest {
            var lookups = 0
            val api =
                Rig(
                    mockEngine {
                        follows(it) {
                            lookups++
                            json(fixture("api/livestream-live.json"))
                        }
                    },
                ).api
            val clock =
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
                }
            val memo = LiveDetailsMemo(clock)
            val sessions = rigSessions(signedIn = true)

            api.followed(sessions, memo).value()
            delay(60_000)
            val again = api.followed(sessions, memo).value()
            assertEquals(2, lookups)
            assertTrue(again.live.all { it.thumbnailUrl == "https://cdn.example/t/9.webp" })

            delay(31_000)
            api.followed(sessions, memo).value()
            assertEquals(4, lookups)
        }

    @Test
    fun followedStopsAtTwentyPages() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(
                            fixture(
                                "account/followed-page1.json",
                            ).replace("\"nextCursor\": 3", "\"nextCursor\": ${seen.size + 100}"),
                        )
                    },
                ).api
            api.followed(rigSessions(signedIn = true)).value()

            assertEquals(20, seen.count { it.url.encodedPath == "/api/v2/channels/followed" })
        }

    @Test
    fun followedStopsWhenTheCursorRepeats() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("account/followed-page1.json"))
                    },
                ).api
            api.followed(rigSessions(signedIn = true)).value()

            assertEquals(2, seen.count { it.url.encodedPath == "/api/v2/channels/followed" })
        }

    @Test
    fun followPathEscapesTheSlug() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("account/follow-ok.json"))
                    },
                ).api
            api.setFollow(rigSessions(signedIn = true), ChannelRef(Platform.KICK, "a/b?c#d"), follow = true)

            assertEquals("/api/v2/channels/a%2Fb%3Fc%23d/follow", seen.single().url.encodedPath)
            assertTrue(
                seen
                    .single()
                    .url.encodedQuery
                    .isEmpty(),
            )
        }

    @Test
    fun signedOutFollowedIsNotSignedIn() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("account/followed-page1.json"))
                    },
                ).api

            assertEquals(Outcome.Failure(LurkError.NotSignedIn), api.followed(rigSessions()))
            assertTrue(seen.isEmpty())
        }

    @Test
    fun followPostsAndUnfollowDeletes() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        if (it.method ==
                            HttpMethod.Post
                        ) {
                            json(fixture("account/follow-ok.json"))
                        } else {
                            respond("", HttpStatusCode.NoContent)
                        }
                    },
                ).api
            val sessions = rigSessions(signedIn = true)

            assertEquals(Outcome.Success(Unit), api.setFollow(sessions, ref, follow = true))
            assertEquals(Outcome.Success(Unit), api.setFollow(sessions, ref, follow = false))

            assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), seen.map { it.method })
            assertTrue(seen.all { it.url.encodedPath == "/api/v2/channels/streamer1/follow" })
            assertTrue(seen.all { it.headers[HttpHeaders.Authorization] == "Bearer $RIG_TOKEN" })
        }

    @Test
    fun signedOutFollowIsNotSignedIn() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("account/follow-ok.json"))
                    },
                ).api

            assertEquals(Outcome.Failure(LurkError.NotSignedIn), api.setFollow(rigSessions(), ref, follow = true))
            assertEquals(Outcome.Failure(LurkError.NotSignedIn), api.setFollow(rigSessions(), ref, follow = false))
            assertTrue(seen.isEmpty())
        }

    @Test
    fun followRejectedByKickKeepsTheError() =
        runTest {
            val api = Rig(mockEngine { json("""{"message":"nope"}""", HttpStatusCode.NotFound) }).api

            assertEquals(
                Outcome.Failure(LurkError.NotFound),
                api.setFollow(rigSessions(signedIn = true), ref, follow = true),
            )
        }
}

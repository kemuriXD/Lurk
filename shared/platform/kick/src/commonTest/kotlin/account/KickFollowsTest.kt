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
import io.github.kemurixd.lurk.platform.kick.fixture
import io.github.kemurixd.lurk.platform.kick.queryOf
import io.github.kemurixd.lurk.platform.kick.rigSessions
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
                    category = Category(Platform.KICK, "", "Category 1", null, null),
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

    @Test
    fun followedStopsAtTwentyPages() =
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

            assertEquals(20, seen.size)
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

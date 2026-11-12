package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
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
import kotlin.time.Instant

class ChannelQueryTest {
    private val ref = ChannelRef(Platform.TWITCH, "streamer1")

    private val lastStream =
        LastStream(
            title = "Stream title 1",
            category = Category(Platform.TWITCH, "100001", "Category One", "https://cdn.example/b/1-285x380.jpg"),
            startedAt = Instant.parse("2026-01-01T10:00:00.123456Z"),
        )

    private fun Outcome<Channel>.loaded(): Channel {
        assertIs<Outcome.Success<Channel>>(this)
        return value
    }

    @Test
    fun mapsTheOfflineProfile() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/channel-offline.json")) }
            val channel = r.gql.channel(r.sessions, ref).loaded()

            assertEquals(
                Channel(
                    ref = ref,
                    id = "1000001",
                    chatId = "streamer1",
                    displayName = "Streamer One",
                    avatarUrl = "https://cdn.example/a/1-300.png",
                    bannerUrl = "https://cdn.example/n/1.png",
                    bio = "Bio 1",
                    followers = 5000,
                    live = null,
                    lastStream = lastStream,
                ),
                channel,
            )
        }

    @Test
    fun liveProfileCarriesTheStream() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/channel-live.json")) }
            val live =
                r.gql
                    .channel(r.sessions, ref)
                    .loaded()
                    .live

            assertNotNull(live)
            assertEquals(12345, live.viewers)
            assertEquals("Stream title 1", live.title)
            assertEquals("https://cdn.example/t/streamer1-440x248.jpg", live.thumbnailUrl)
        }

    @Test
    fun unknownChannelIsNotFound() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/channel-unknown.json")) }

            assertEquals(Outcome.Failure(LurkError.NotFound), r.gql.channel(r.sessions, ref))
        }

    @Test
    fun nullGameIsNullCategory() =
        runTest {
            val body =
                fixture("gql/channel-offline.json").replace(
                    "\"game\":{\"id\"",
                    "\"game\":null,\"other\":{\"id\"",
                )
            val r = discoveryRig { reply(body) }

            assertNull(
                r.gql
                    .channel(r.sessions, ref)
                    .loaded()
                    .lastStream
                    ?.category,
            )
        }

    @Test
    fun noLastBroadcastIsNoLastStream() =
        runTest {
            val body = fixture("gql/channel-offline.json").replace("\"lastBroadcast\":", "\"somethingElse\":")
            val r = discoveryRig { reply(body) }

            assertNull(
                r.gql
                    .channel(r.sessions, ref)
                    .loaded()
                    .lastStream,
            )
        }

    @Test
    fun asksForTheLogin() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/channel-offline.json")) }
            r.gql.channel(r.sessions, ref)

            val op = r.operation()
            assertEquals("streamer1", op["variables"]!!.jsonObject["login"]!!.jsonPrimitive.content)
            val query = op["query"]!!.jsonPrimitive.content
            assertTrue("user(login: \$login)" in query, query)
            assertTrue("profileImageURL(width: 300)" in query, query)
            assertTrue("followers { totalCount }" in query, query)
            assertTrue("lastBroadcast { title startedAt game" in query, query)
        }
}

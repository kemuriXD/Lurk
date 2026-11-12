package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmoteApiTest {
    private fun api(
        status: HttpStatusCode,
        body: String = "",
    ) = EmoteApi(createHttpClient(MockEngine { respond(body, status) }))

    @Test
    fun bttvUserOn404IsEmpty() =
        runTest {
            assertTrue(api(HttpStatusCode.NotFound).bttvUser("1000500", emptySet()).isEmpty())
        }

    @Test
    fun ffzRoomOn404IsEmpty() =
        runTest {
            assertTrue(api(HttpStatusCode.NotFound).ffzRoom("1000500").isEmpty())
        }

    @Test
    fun sevenTvUserOn404HasNoSetAndNoEmotes() =
        runTest {
            val user = api(HttpStatusCode.NotFound).sevenTvUser(Platform.KICK, "1")
            assertNull(user.emoteSetId)
            assertTrue(user.emotes.isEmpty())
        }

    @Test
    fun sevenTvUserReturnsSetId() =
        runTest {
            val user =
                api(HttpStatusCode.OK, fixture("seventv/user-twitch.json")).sevenTvUser(Platform.TWITCH, "1000500")
            assertEquals("01AA0000000000000000000011", user.emoteSetId)
            assertEquals(6, user.emotes.size)
        }

    @Test
    fun serverErrorThrowsHttp500() =
        runTest {
            val e = assertFailsWith<LurkException> { api(HttpStatusCode.InternalServerError).bttvGlobal(emptySet()) }
            assertEquals(LurkError.Http(500), e.error)
            assertFailsWith<LurkException> { api(HttpStatusCode.InternalServerError).bttvUser("1", emptySet()) }
        }

    @Test
    fun requestsUseTheDocumentedEndpoints() =
        runTest {
            // A readable body for every endpoint called here: an object with BTTV emote arrays and FFZ sets.
            val engine = MockEngine { respond("""{"channelEmotes":[],"sets":{}}""", HttpStatusCode.OK) }
            val api = EmoteApi(createHttpClient(engine))
            api.sevenTvGlobal()
            api.sevenTvUser(Platform.KICK, "42")
            api.sevenTvSet("setid")
            api.bttvUser("7", emptySet())
            api.ffzRoom("7")
            api.ffzGlobal()
            assertEquals(
                listOf(
                    "https://7tv.io/v3/emote-sets/global",
                    "https://7tv.io/v3/users/kick/42",
                    "https://7tv.io/v3/emote-sets/setid",
                    "https://api.betterttv.net/3/cached/users/twitch/7",
                    "https://api.frankerfacez.com/v1/room/id/7",
                    "https://api.frankerfacez.com/v1/set/global",
                ),
                engine.requestHistory.map { it.url.toString() },
            )
        }

    @Test
    fun unreadableBodyThrowsSchemaChanged() =
        runTest {
            val html = api(HttpStatusCode.OK, fixture("broken/html.json"))
            val calls: List<suspend () -> Any> =
                listOf(
                    { html.sevenTvGlobal() },
                    { html.sevenTvUser(Platform.TWITCH, "1000500") },
                    { html.sevenTvSet("01AA0000000000000000000011") },
                    { html.bttvGlobal(emptySet()) },
                    { html.bttvUser("1000500", emptySet()) },
                    { html.ffzGlobal() },
                    { html.ffzRoom("1000500") },
                )
            calls.forEachIndexed { index, call ->
                val e = assertFailsWith<LurkException>("call $index") { call() }
                assertEquals(LurkError.SchemaChanged, e.error, "call $index")
            }
        }

    @Test
    fun wrongShapeThrowsSchemaChanged() =
        runTest {
            val wrong = api(HttpStatusCode.OK, """{"error":"maintenance"}""")
            assertEquals(LurkError.SchemaChanged, assertFailsWith<LurkException> { wrong.ffzRoom("1") }.error)
            assertEquals(LurkError.SchemaChanged, assertFailsWith<LurkException> { wrong.ffzGlobal() }.error)
            assertEquals(
                LurkError.SchemaChanged,
                assertFailsWith<LurkException> { wrong.bttvUser("1", emptySet()) }.error,
            )
            assertEquals(LurkError.SchemaChanged, assertFailsWith<LurkException> { wrong.bttvGlobal(emptySet()) }.error)
        }

    @Test
    fun readableEmptyBodyIsEmpty() =
        runTest {
            val bttv = api(HttpStatusCode.OK, """{"channelEmotes":[],"sharedEmotes":[]}""")
            assertTrue(bttv.bttvUser("1", emptySet()).isEmpty())
            assertTrue(api(HttpStatusCode.OK, "[]").bttvGlobal(emptySet()).isEmpty())
            assertTrue(api(HttpStatusCode.OK, """{"room":{},"sets":{}}""").ffzRoom("1").isEmpty())
            assertTrue(api(HttpStatusCode.OK, """{"id":"x","emotes":[]}""").sevenTvSet("x").isEmpty())
            assertNull(
                api(HttpStatusCode.OK, """{"id":"u","emote_set":null}""").sevenTvUser(Platform.KICK, "1").emoteSetId,
            )
        }
}

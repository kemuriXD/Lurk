package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.platform.kick.RIG_TOKEN
import io.github.kemurixd.lurk.platform.kick.api.json
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealtimeBootstrapTest {
    private fun Outcome<RealtimeTicket>.ticket(): RealtimeTicket {
        assertIs<Outcome.Success<RealtimeTicket>>(this)
        return value
    }

    @Test
    fun bootstrapBodiesMatchWebClient() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            bootstrapRig(seen).open(CHANNEL_ID, CHATROOM_ID, token = null, userId = "1000099").ticket()

            val connection =
                """{"client":{"id":"1000099","type":"web"},""" +
                    """"capabilities":{"accepted_providers":[{"provider":"pusher"},{"provider":"centrifugo"}]}}"""
            assertEquals(listOf(connection, connection, """{"client_id":"1000099"}"""), seen.map { it.bodyText() })
        }

    @Test
    fun signedOutClientIdIsOneUuidAcrossTheCalls() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            bootstrapRig(seen).open(CHANNEL_ID, CHATROOM_ID, token = null).ticket()

            val uuid = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}""")
            val ids = seen.map { uuid.find(it.bodyText().orEmpty())?.value }
            assertEquals(3, ids.size)
            assertTrue(ids.all { it != null && it == ids[0] }, "one UUID shared by all three calls")
            assertTrue(seen.none { it.headers[HttpHeaders.Authorization] != null })
        }

    @Test
    fun bootstrapOrder() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            bootstrapRig(seen).open(CHANNEL_ID, CHATROOM_ID, token = RIG_TOKEN, userId = "1000099").ticket()

            assertEquals(listOf(CONNECTION_PATH, CHAT_CONNECTION_PATH, AUTH_PATH), seen.map { it.url.encodedPath })
            assertTrue(seen.all { it.method == HttpMethod.Post })
            assertTrue(seen.all { it.url.host == "web.kick.example" })
            assertTrue(seen.all { it.headers[HttpHeaders.Authorization] == "Bearer $RIG_TOKEN" })
        }

    @Test
    fun channelsFromTemplates() =
        runTest {
            val ticket = bootstrapRig(mutableListOf()).open(CHANNEL_ID, CHATROOM_ID, token = null).ticket()

            assertEquals(
                listOf(
                    "chatroom_$CHATROOM_ID",
                    "channel_$CHANNEL_ID",
                    "chatrooms.$CHATROOM_ID",
                    "chatrooms.$CHATROOM_ID.v2",
                    "channel.$CHANNEL_ID",
                ),
                ticket.channels,
            )
            assertEquals(SOCKET_URL, ticket.socketUrl)
            assertEquals(FIXTURE_TOKEN, ticket.connectToken)
        }

    @Test
    fun pusherChosenIsSchemaChanged() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val pusherFirst =
                """{"data":{"connections":[""" +
                    """{"credentials":{"app_key":"key1","cluster":"us2"},"provider":"pusher"},""" +
                    """{"credentials":{"url":"$SOCKET_URL"},"provider":"centrifugo"}""" +
                    """],"mode":"websocket"},"message":"success"}"""
            val outcome =
                bootstrapRig(seen) {
                    if (it.url.encodedPath == CHAT_CONNECTION_PATH) json(pusherFirst) else recorded(it)
                }.open(CHANNEL_ID, CHATROOM_ID, token = null)

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcome)
            assertFalse(
                seen.any { it.url.encodedPath == AUTH_PATH },
                "no token is fetched for a socket Lurk can't open",
            )
        }

    @Test
    fun unusableConnectionsAreSchemaChanged() =
        runTest {
            val replies =
                listOf(
                    """{"data":{"connections":[],"mode":"websocket"}}""",
                    """{"data":{"connections":[{"credentials":{},"provider":"centrifugo"}],"mode":"websocket"}}""",
                    """{"data":{"connections":[{"credentials":{"url":"$SOCKET_URL"},"provider":"centrifugo"}],""" +
                        """"mode":"polling"}}""",
                    """{"message":"success"}""",
                )
            replies.forEach { reply ->
                val outcome =
                    bootstrapRig(mutableListOf()) {
                        if (it.url.encodedPath == CHAT_CONNECTION_PATH) json(reply) else recorded(it)
                    }.open(CHANNEL_ID, CHATROOM_ID, token = null)
                assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcome, reply)
            }
        }

    @Test
    fun authWithoutTokenIsSchemaChanged() =
        runTest {
            val outcome =
                bootstrapRig(mutableListOf()) {
                    recorded(it, auth = { """{"data":{"token":""},"message":"success"}""" })
                }.open(CHANNEL_ID, CHATROOM_ID, token = null)

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcome)
        }

    @Test
    fun ticketToStringHidesToken() {
        val ticket = RealtimeTicket(SOCKET_URL, "eyJsecret.payload.sig", listOf("chatrooms.1.v2"))

        assertFalse("eyJsecret" in ticket.toString(), ticket.toString())
        assertTrue(SOCKET_URL in ticket.toString())
    }
}

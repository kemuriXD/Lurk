package io.github.kemurixd.lurk.platform.kick.chat

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.platform.kick.RIG_TOKEN
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class KickSenderTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_123)
        }
    private val context = KickReplyContext("msg-1", "original text", "1000002", "user_0002")

    private fun HttpRequestData.bodyJson(): JsonObject = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private fun TestScope.sender(
        seen: MutableList<HttpRequestData>,
        token: String? = RIG_TOKEN,
        known: KickReplyContext? = context,
    ) = KickSender(
        api =
            Rig(
                mockEngine {
                    seen += it
                    json(fixture("chat/send-ok.json"))
                },
            ).api,
        chatroomId = "1000100",
        token = { token },
        replyContext = { id -> known?.takeIf { it.messageId == id } },
        clock = clock,
    )

    @Test
    fun sendBody() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()

            assertEquals(Outcome.Success(Unit), sender(seen).send("hello there", null))

            val request = seen.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/messages/send/1000100", request.url.encodedPath)
            assertEquals("Bearer $RIG_TOKEN", request.headers[HttpHeaders.Authorization])
            val body = request.bodyJson()
            assertEquals(setOf("content", "type", "message_ref"), body.keys)
            assertEquals("hello there", body.getValue("content").jsonPrimitive.content)
            assertEquals("message", body.getValue("type").jsonPrimitive.content)
            assertEquals("1700000000123", body.getValue("message_ref").jsonPrimitive.content)
        }

    @Test
    fun replyBodyHasMetadata() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()

            assertEquals(Outcome.Success(Unit), sender(seen).send("agreed", "msg-1"))

            val body = seen.single().bodyJson()
            assertEquals("reply", body.getValue("type").jsonPrimitive.content)
            assertEquals("agreed", body.getValue("content").jsonPrimitive.content)
            val metadata = body.getValue("metadata").jsonObject
            val original = metadata.getValue("original_message").jsonObject
            assertEquals("msg-1", original.getValue("id").jsonPrimitive.content)
            assertEquals("original text", original.getValue("content").jsonPrimitive.content)
            val from = metadata.getValue("original_sender").jsonObject
            assertEquals("1000002", from.getValue("id").jsonPrimitive.content)
            assertEquals("user_0002", from.getValue("username").jsonPrimitive.content)
        }

    @Test
    fun unknownReplySentPlain() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()

            assertEquals(Outcome.Success(Unit), sender(seen).send("agreed", "other-id"))

            val body = seen.single().bodyJson()
            assertEquals("message", body.getValue("type").jsonPrimitive.content)
            assertTrue("metadata" !in body)
        }

    @Test
    fun signedOutSendIsNotSignedIn() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()

            assertEquals(Outcome.Failure(LurkError.NotSignedIn), sender(seen, token = null).send("hi", null))
            assertTrue(seen.isEmpty())
        }
}

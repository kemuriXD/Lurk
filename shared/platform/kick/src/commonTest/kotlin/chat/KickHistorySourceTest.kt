package io.github.kemurixd.lurk.platform.kick.chat

import io.github.kemurixd.lurk.chat.ChatEvent
import io.github.kemurixd.lurk.chat.ChatParsers
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KickHistorySourceTest {
    @Test
    fun historyReturnsRawBody() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val body = fixture("chat/history.json")
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(body)
                    },
                ).api

            val text = KickHistorySource(api, "1000001").fetch()

            assertEquals(HttpMethod.Get, seen.single().method)
            assertEquals("/api/v1/chat/1000001/history", seen.single().url.encodedPath)
            assertEquals(Json.parseToJsonElement(body), Json.parseToJsonElement(text))
            val events = ChatParsers.history(Platform.KICK)(text)
            assertTrue(events.isNotEmpty())
            assertTrue(events.all { it is ChatEvent.Message && it.message.historical })
            assertEquals(
                listOf("first message", "second message", "third message chanWave"),
                events.map { (it as ChatEvent.Message).message.text },
            )
        }
}

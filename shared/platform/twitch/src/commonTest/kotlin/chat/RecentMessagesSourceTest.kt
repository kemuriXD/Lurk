package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecentMessagesSourceTest {
    @Test
    fun historyReplacesChannel() =
        runTest {
            var url = ""
            val engine =
                MockEngine { request ->
                    url = request.url.toString()
                    respond("""{"messages":[]}""", HttpStatusCode.OK)
                }
            val body = RecentMessagesSource(createHttpClient(engine), chatConfig, "streamer1").fetch()
            assertEquals("https://recent.example/api/streamer1?limit=50", url)
            assertEquals("""{"messages":[]}""", body)
        }

    @Test
    fun historyErrorThrows() =
        runTest {
            val engine = MockEngine { respond("nope", HttpStatusCode.NotFound) }
            val e =
                assertFailsWith<LurkException> {
                    RecentMessagesSource(createHttpClient(engine), chatConfig, "streamer1").fetch()
                }
            assertEquals(LurkError.NotFound, e.error)
        }
}

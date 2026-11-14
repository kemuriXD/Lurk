package io.github.kemurixd.lurk.platform.kick.channel

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class KickPastStreamJsonTest {
    private fun latest(text: String) = latestPastStream(Json.parseToJsonElement(text))

    @Test
    fun newestFinishedBroadcastWinsWhateverTheOrder() {
        val past =
            latest(
                """
                [
                  {"is_live":false,"start_time":"2026-01-01 10:00:00","session_title":"Old"},
                  {"is_live":true,"start_time":"2026-01-03 10:00:00","session_title":"Live now"},
                  {"is_live":false,"start_time":"2026-01-02 10:00:00","session_title":"New"},
                  {"is_live":false,"start_time":"not a time"}
                ]
                """.trimIndent(),
            )

        assertEquals(PastStream("New", Instant.parse("2026-01-02T10:00:00Z")), past)
    }

    @Test
    fun anythingButAListHasNoPastStream() {
        assertNull(latest("""{"message":"nope"}"""))
        assertNull(latest("[]"))
    }
}

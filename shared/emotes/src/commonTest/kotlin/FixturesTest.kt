package io.github.kemurixd.lurk.emotes

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixturesTest {
    private val json =
        listOf(
            "seventv/global.json",
            "seventv/user-twitch.json",
            "seventv/user-kick.json",
            "seventv/emote-set.json",
            "seventv/eventapi/hello.json",
            "seventv/eventapi/heartbeat.json",
            "seventv/eventapi/ack-resume.json",
            "seventv/eventapi/ack-resume-failed.json",
            "seventv/eventapi/reconnect.json",
            "seventv/eventapi/end-of-stream.json",
            "seventv/eventapi/dispatch-emote-set-update.json",
            "seventv/eventapi/dispatch-cosmetic-paint.json",
            "seventv/eventapi/dispatch-cosmetic-badge.json",
            "seventv/eventapi/dispatch-entitlement-paint.json",
            "seventv/eventapi/dispatch-entitlement-emote-set.json",
            "seventv/eventapi/dispatch-entitlement-delete.json",
            "seventv/eventapi/dispatch-entitlement-kick-only.json",
            "bttv/global.json",
            "bttv/user.json",
            "ffz/global.json",
            "ffz/room.json",
            "broken/seventv-one-bad-emote.json",
        )

    @Test
    fun everyFixtureExistsAndIsNotEmpty() {
        (json + "README.md" + "broken/html.json").forEach { assertTrue(fixture(it).isNotBlank(), "$it is empty") }
    }

    @Test
    fun jsonFixturesParse() {
        json.forEach { Json.parseToJsonElement(fixture(it)) }
    }

    @Test
    fun htmlFixtureIsNotJson() {
        assertFailsWith<Exception> { Json.parseToJsonElement(fixture("broken/html.json")) }
    }
}

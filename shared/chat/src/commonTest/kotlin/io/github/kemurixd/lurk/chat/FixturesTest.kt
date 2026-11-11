package io.github.kemurixd.lurk.chat

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class FixturesTest {
    private val files =
        listOf(
            "README.md",
            "twitch/chat.irc",
            "twitch/recent-messages.json",
            "kick/frames.jsonl",
            "kick/history.json",
        )

    @Test
    fun everyFixtureExistsAndIsNotEmpty() {
        files.forEach { assertTrue(fixture(it).isNotBlank(), "$it is empty") }
    }

    @Test
    fun jsonFixturesParse() {
        files.filter { it.endsWith(".json") }.forEach { Json.parseToJsonElement(fixture(it)) }
    }

    @Test
    fun everyJsonlLineParses() {
        files.filter { it.endsWith(".jsonl") }.forEach { name ->
            fixture(name).lines().filter { it.isNotBlank() }.forEachIndexed { index, line ->
                runCatching { Json.parseToJsonElement(line) }
                    .onFailure { throw AssertionError("$name line ${index + 1} does not parse", it) }
            }
        }
    }

    @Test
    fun ircFixtureHasAtLeastFortyLines() {
        assertTrue(fixture("twitch/chat.irc").lines().count { it.isNotBlank() } >= 40)
    }
}

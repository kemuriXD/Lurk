package io.github.kemurixd.lurk.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConfigValidatorTest {
    @Test
    fun validConfigHasNoErrors() {
        assertEquals(emptyList(), RemoteConfigValidator.errors(validConfig()))
    }

    @Test
    fun badHashNamesTheQuery() {
        val base = validConfig()
        val config = base.copy(twitch = base.twitch.copy(persistedQueries = mapOf("BrokenQuery" to "xyz")))
        val errors = RemoteConfigValidator.errors(config)
        assertEquals(1, errors.size)
        assertTrue("BrokenQuery" in errors.single(), errors.single())
    }

    @Test
    fun newerSchemaIsAnError() {
        assertEquals(1, RemoteConfigValidator.errors(validConfig().copy(schemaVersion = 3)).size)
    }

    @Test
    fun templateWithoutPlaceholderIsAnError() {
        val base = validConfig()
        val config = base.copy(kick = base.kick.copy(publicChannelTemplates = listOf("chatroom")))
        assertEquals(1, RemoteConfigValidator.errors(config).size)
    }

    @Test
    fun blankClientIdEmptyAdCuesAndNonHttpsUrlsAreErrors() {
        val base = validConfig()
        val config =
            base.copy(
                twitch =
                    base.twitch.copy(
                        gqlClientId = " ",
                        adCueClasses = emptyList(),
                        recentMessagesUrl = "http://recent.example.test/{channel}",
                    ),
                kick = base.kick.copy(apiBase = "http://kick.example.test/", webApiBase = "ftp://x"),
            )
        assertEquals(5, RemoteConfigValidator.errors(config).size)
    }
}

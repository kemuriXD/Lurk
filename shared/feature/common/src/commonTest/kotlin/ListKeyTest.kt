package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ListKeyTest {
    @Test
    fun keyIsPlatformAndLowercaseLogin() {
        assertEquals("TWITCH:streamer1", ChannelRef(Platform.TWITCH, "Streamer1").key)
    }

    @Test
    fun sameLoginOnBothPlatformsGivesDistinctKeys() {
        val onTwitch = testStream(Platform.TWITCH)
        val onKick = testStream(Platform.KICK)

        assertNotEquals(onTwitch.key, onKick.key)
        assertNotEquals(testChannel(Platform.TWITCH).key, testChannel(Platform.KICK).key)
    }

    @Test
    fun blankIdKickItemsKeepDistinctKeys() {
        val live = listOf("streamer1", "streamer2").map { testStream(Platform.KICK, login = it, channelId = "") }
        val offline = listOf("streamer1", "streamer2").map { testChannel(Platform.KICK, login = it, id = "") }

        assertNotEquals(live[0].key, live[1].key)
        assertNotEquals(offline[0].key, offline[1].key)
    }

    @Test
    fun streamAndChannelKeysMatchTheirRef() {
        val ref = ChannelRef(Platform.KICK, "streamer1")

        assertEquals(ref.key, testStream(Platform.KICK).key)
        assertEquals(ref.key, testChannel(Platform.KICK).key)
    }
}

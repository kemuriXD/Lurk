package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeStreamsTest {
    @Test
    fun sortsByViewersThenName() {
        val small = testStream(Platform.KICK, login = "small", viewers = 50, displayName = "Bee")
        val zed = testStream(Platform.TWITCH, login = "zed", viewers = 100, displayName = "zed")
        val alphaKick = testStream(Platform.KICK, login = "alpha", viewers = 100, displayName = "alpha")
        val alphaTwitch = testStream(Platform.TWITCH, login = "alpha", viewers = 100, displayName = "Alpha")
        val big = testStream(Platform.KICK, login = "big", viewers = 900, displayName = "Big")

        val sorted = listOf(small, zed, alphaKick, alphaTwitch, big).sortedByViewers()

        assertEquals(listOf(big, alphaTwitch, alphaKick, zed, small), sorted)
    }
}

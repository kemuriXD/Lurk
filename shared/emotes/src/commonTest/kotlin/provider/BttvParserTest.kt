package io.github.kemurixd.lurk.emotes.provider

import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.emotes.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BttvParserTest {
    private val zeroWidth =
        setOf("SoSnowy", "IceCold", "SantaHat", "TopHat", "ReinDeer", "CandyCane", "cvMask", "cvHazmat")

    @Test
    fun userCombinesChannelAndSharedEmotes() {
        val emotes = BttvParser.user(fixture("bttv/user.json"), zeroWidth)
        assertEquals(listOf("chanPls", "chanHappyLeft", "chanDance", "PepePls", "EZ", "Clap"), emotes.map { it.name })
        assertTrue(emotes.all { it.scope == EmoteScope.CHANNEL && it.provider == EmoteProvider.BTTV })
    }

    @Test
    fun globalIsGlobalScope() {
        val emotes = BttvParser.global(fixture("bttv/global.json"), zeroWidth)
        assertEquals(6, emotes.size)
        assertTrue(emotes.all { it.scope == EmoteScope.GLOBAL })
    }

    @Test
    fun zeroWidthNamesComeFromTheParameter() {
        val flagged = BttvParser.user(fixture("bttv/user.json"), setOf("Clap"))
        assertEquals(listOf("Clap"), flagged.filter { it.zeroWidth }.map { it.name })
        assertTrue(BttvParser.user(fixture("bttv/user.json"), emptySet()).none { it.zeroWidth })
    }

    @Test
    fun imagesUseTheCdnWithScalesOneToThree() {
        val emote = BttvParser.user(fixture("bttv/user.json"), zeroWidth).first()
        assertEquals(listOf(1, 2, 3), emote.images.map { it.scale })
        assertEquals("https://cdn.betterttv.net/emote/${emote.id}/1x", emote.images.first().url)
        assertTrue(emote.animated)
    }

    @Test
    fun badItemSkipped() {
        val json =
            """[{"id":"a","code":"A","imageType":"png","animated":false},{"id":"b"},
            {"id":"c","code":"C","imageType":"png","animated":false}]"""
        assertEquals(listOf("A", "C"), BttvParser.global(json, zeroWidth).map { it.name })
    }

    @Test
    fun htmlBodyGivesEmptyList() {
        assertTrue(BttvParser.global(fixture("broken/html.json"), zeroWidth).isEmpty())
        assertTrue(BttvParser.user(fixture("broken/html.json"), zeroWidth).isEmpty())
    }
}

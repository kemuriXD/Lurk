package io.github.kemurixd.lurk.emotes.provider

import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.FfzEffect
import io.github.kemurixd.lurk.emotes.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfzParserTest {
    @Test
    fun globalReadsOnlyDefaultSets() {
        val json =
            """
            {"default_sets":[3],"sets":{
              "3":{"emoticons":[{"id":1,"name":"In","urls":{"1":"https://cdn.frankerfacez.com/emote/1/1"}}]},
              "4":{"emoticons":[{"id":2,"name":"Out","urls":{"1":"https://cdn.frankerfacez.com/emote/2/1"}}]}}}
            """.trimIndent()
        assertEquals(listOf("In"), FfzParser.global(json).map { it.name })
        val emotes = FfzParser.global(fixture("ffz/global.json"))
        assertEquals(6, emotes.size)
        assertTrue(emotes.all { it.scope == EmoteScope.GLOBAL && it.provider == EmoteProvider.FFZ })
    }

    @Test
    fun roomReadsEverySetAsChannelScope() {
        val emotes = FfzParser.room(fixture("ffz/room.json"))
        assertEquals(listOf("widepeepoHappy", "YEP", "PepeHands"), emotes.map { it.name })
        assertTrue(emotes.all { it.scope == EmoteScope.CHANNEL })
    }

    @Test
    fun modifierEmoteCarriesEffects() {
        val emotes = FfzParser.global(fixture("ffz/global.json"))
        val hyper = emotes.single { it.name == "ffzHyper" }
        assertEquals(FfzEffect.fromFlags(12289), hyper.modifierEffects)
        assertTrue(hyper.isModifier)
        assertEquals(setOf(FfzEffect.HIDDEN, FfzEffect.GROW_X), emotes.single { it.name == "ffzW" }.modifierEffects)
        assertTrue(emotes.single { it.name == "YooHoo" }.modifierEffects.isEmpty())
    }

    @Test
    fun hiddenEmoteIsKeptAndHiddenInPicker() {
        val emotes = FfzParser.global(fixture("ffz/global.json"))
        assertTrue(emotes.single { it.name == "ZrehplaR" }.hiddenInPicker)
        assertFalse(emotes.single { it.name == "YooHoo" }.hiddenInPicker)
    }

    @Test
    fun hiddenModifierBitHidesFromPicker() {
        val emotes = FfzParser.global(fixture("ffz/global.json"))
        assertTrue(emotes.single { it.name == "ffzW" }.hiddenInPicker)
    }

    @Test
    fun staticImagesUseUrlsWithScalesOneTwoFour() {
        val emote = FfzParser.global(fixture("ffz/global.json")).first()
        assertEquals(listOf(1, 2, 4), emote.images.map { it.scale })
        assertFalse(emote.animated)
    }

    @Test
    fun animatedImagesPreferTheAnimatedMap() {
        val json =
            """{"default_sets":[1],"sets":{"1":{"emoticons":[{"id":9,"name":"Moving",
            "urls":{"1":"https://cdn.frankerfacez.com/emote/9/1"},
            "animated":{"1":"https://cdn.frankerfacez.com/emote/9/animated/1",
            "2":"https://cdn.frankerfacez.com/emote/9/animated/2"}}]}}}"""
        val emote = FfzParser.global(json).single()
        assertTrue(emote.animated)
        assertEquals(listOf(1, 2), emote.images.map { it.scale })
        assertTrue(emote.images.all { "animated" in it.url })
    }

    @Test
    fun badItemSkipped() {
        val json =
            """{"default_sets":[1],"sets":{"1":{"emoticons":[
            {"id":1,"name":"A","urls":{"1":"u"}},{"id":"nope","name":5},{"id":3,"name":"C","urls":{"1":"u"}}]}}}"""
        assertEquals(listOf("A", "C"), FfzParser.global(json).map { it.name })
    }

    @Test
    fun htmlBodyGivesEmptyList() {
        assertTrue(FfzParser.global(fixture("broken/html.json")).isEmpty())
        assertTrue(FfzParser.room(fixture("broken/html.json")).isEmpty())
    }

    @Test
    fun nullScaleKeepsTheEmote() {
        val json =
            """
            {"sets":{"9":{"emoticons":[
              {"id":1,"name":"Still","urls":{"1":"https://cdn.frankerfacez.com/emote/1/1","2":null,"4":"https://cdn.frankerfacez.com/emote/1/4"}},
              {"id":2,"name":"Moving","urls":{"1":"https://cdn.frankerfacez.com/emote/2/1"},"animated":{"1":"https://cdn.frankerfacez.com/emote/2/animated/1","2":null}}
            ]}}}
            """.trimIndent()
        val emotes = FfzParser.room(json)
        assertEquals(listOf("Still", "Moving"), emotes.map { it.name })
        assertEquals(listOf(1, 4), emotes[0].images.map { it.scale })
        assertEquals(listOf("https://cdn.frankerfacez.com/emote/2/animated/1"), emotes[1].images.map { it.url })
        assertTrue(emotes[1].animated)
    }
}

package io.github.kemurixd.lurk.emotes.provider

import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.emotes.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SevenTvParserTest {
    @Test
    fun globalSetParsesAllSixEmotes() {
        val emotes = SevenTvParser.emoteSet(fixture("seventv/global.json"), EmoteScope.GLOBAL)
        assertEquals(6, emotes.size)
        assertTrue(emotes.all { it.provider == EmoteProvider.SEVENTV && it.scope == EmoteScope.GLOBAL })
    }

    @Test
    fun zeroWidthActiveEmoteIsFlagged() {
        val emotes = SevenTvParser.emoteSet(fixture("seventv/global.json"), EmoteScope.GLOBAL)
        assertEquals(listOf("RainTime", "PETPET"), emotes.filter { it.zeroWidth }.map { it.name })
    }

    @Test
    fun imagesAreWebpOnTheSevenTvCdnWithScalesOneToFour() {
        val emote = SevenTvParser.emoteSet(fixture("seventv/global.json"), EmoteScope.GLOBAL).first()
        assertEquals(listOf(1, 2, 3, 4), emote.images.map { it.scale })
        assertTrue(emote.images.all { it.url.startsWith("https://cdn.7tv.app/") && it.url.endsWith(".webp") })
        assertEquals("https://cdn.7tv.app/emote/${emote.id}/1x.webp", emote.images.first().url)
    }

    @Test
    fun userReadsSetIdAndChannelScope() {
        val user = SevenTvParser.user(fixture("seventv/user-twitch.json"))
        assertEquals("01AA0000000000000000000011", user.emoteSetId)
        assertEquals(6, user.emotes.size)
        assertTrue(user.emotes.all { it.scope == EmoteScope.CHANNEL })
    }

    @Test
    fun unlistedEmoteIsKeptWithListedFalse() {
        val user = SevenTvParser.user(fixture("seventv/user-twitch.json"))
        assertEquals(listOf("DrakeWide", "munch"), user.emotes.filter { !it.listed }.map { it.name })
    }

    @Test
    fun contentFlagsKeepRawBits() {
        val user = SevenTvParser.user(fixture("seventv/user-twitch.json"))
        val thinking = user.emotes.single { it.name == "Thinking" }
        assertEquals(65536, thinking.contentFlags)
        assertTrue(thinking.isSexual)
    }

    @Test
    fun activeNameWinsOverDataName() {
        val json =
            """
            {"emotes":[{"id":"e1","name":"Renamed","flags":0,"data":{"id":"e1","name":"Original","flags":0,
            "listed":true,"animated":false,"host":{"url":"//cdn.7tv.app/emote/e1","files":
            [{"name":"1x.webp","format":"WEBP"}]}}}]}
            """.trimIndent()
        assertEquals("Renamed", SevenTvParser.emoteSet(json, EmoteScope.CHANNEL).single().name)
    }

    @Test
    fun userWithoutEmoteSetGivesNullIdAndNoEmotes() {
        val user = SevenTvParser.user("""{"id":"1","platform":"TWITCH"}""")
        assertNull(user.emoteSetId)
        assertTrue(user.emotes.isEmpty())
    }

    @Test
    fun badItemSkipped() {
        val emotes = SevenTvParser.emoteSet(fixture("broken/seventv-one-bad-emote.json"), EmoteScope.CHANNEL)
        assertEquals(2, emotes.size)
    }

    @Test
    fun htmlBodyGivesEmptyResults() {
        assertTrue(SevenTvParser.emoteSet(fixture("broken/html.json"), EmoteScope.GLOBAL).isEmpty())
        val user = SevenTvParser.user(fixture("broken/html.json"))
        assertNull(user.emoteSetId)
        assertTrue(user.emotes.isEmpty())
    }

    @Test
    fun activeEmoteReturnsNullForBadElement() {
        assertNull(SevenTvParser.activeEmote(providerJson.parseToJsonElement("""{"id":"x"}"""), EmoteScope.CHANNEL))
        val ok =
            providerJson.parseToJsonElement(
                """{"id":"e1","name":"A","flags":0,"data":{"id":"e1","name":"A","flags":0,"listed":true,
                "animated":true,"host":{"url":"//cdn.7tv.app/emote/e1","files":[]}}}""",
            )
        assertNotNull(SevenTvParser.activeEmote(ok, EmoteScope.CHANNEL))
    }
}

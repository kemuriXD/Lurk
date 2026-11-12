package io.github.kemurixd.lurk.chat.kick

import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import kotlin.test.Test
import kotlin.test.assertEquals

class KickContentTest {
    @Test
    fun emoteTokenBecomesNameWithSpan() {
        val (text, spans) = KickContent.split("hi [emote:37226:KEKW] x")
        assertEquals("hi KEKW x", text)
        assertEquals(1, spans.size)
        val span = spans.single()
        assertEquals(3, span.start)
        assertEquals(7, span.end)
        assertEquals("37226", span.emote.id)
        assertEquals("KEKW", span.emote.name)
        assertEquals(EmoteProvider.KICK, span.emote.provider)
        assertEquals(EmoteScope.CHANNEL, span.emote.scope)
        assertEquals(
            "https://files.kick.com/emotes/37226/fullsize",
            span.emote.images
                .single()
                .url,
        )
        assertEquals(
            1,
            span.emote.images
                .single()
                .scale,
        )
    }

    @Test
    fun malformedTokenStaysAsText() {
        val (text, spans) = KickContent.split("[emote:bad]")
        assertEquals("[emote:bad]", text)
        assertEquals(emptyList(), spans)
        assertEquals("[emote:12:]", KickContent.split("[emote:12:]").first)
        assertEquals("[emote::x]", KickContent.split("[emote::x]").first)
    }

    @Test
    fun adjacentTokensGetSeparateSpans() {
        val (text, spans) = KickContent.split("[emote:1:aa][emote:2:bbb]!")
        assertEquals("aabbb!", text)
        assertEquals(listOf(0 to 2, 2 to 5), spans.map { it.start to it.end })
    }

    @Test
    fun plainTextHasNoSpans() {
        assertEquals("hello" to emptyList(), KickContent.split("hello"))
        assertEquals("" to emptyList(), KickContent.split(""))
    }
}

package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatModelTest {
    @Test
    fun platformSerialisesToLowercaseName() {
        assertEquals("\"kick\"", Json.encodeToString(Platform.KICK))
        assertEquals(Platform.TWITCH, Json.decodeFromString<Platform>("\"twitch\""))
    }

    @Test
    fun ffzEffectsDecodeFromFlagBits() {
        assertEquals(setOf(FfzEffect.FLIP_X, FfzEffect.NO_SPACE), FfzEffect.fromFlags((1 shl 1) or (1 shl 17)))
        assertEquals(emptySet(), FfzEffect.fromFlags(0))
    }

    @Test
    fun ffzEffectBitsMatchFrankerFaceZ() {
        // FrankerFaceZ make_enum_flags: Hidden, FlipX, ... Shake, Cursed, Jam, Bounce, NoSpace, one bit each in order.
        assertEquals((0..17).toList(), FfzEffect.entries.map { it.bit.countTrailingZeroBits() })
        assertEquals(1 shl 14, FfzEffect.CURSED.bit)
        assertEquals(1 shl 17, FfzEffect.NO_SPACE.bit)
    }

    @Test
    fun contentFlagsExposeSevenTvVisibilityBits() {
        val emote = emote(contentFlags = (1 shl 16) or (1 shl 24))
        assertTrue(emote.isSexual)
        assertFalse(emote.isEpilepsy)
        assertFalse(emote.isEdgy)
        assertEquals((1 shl 16) or (1 shl 24), emote.contentFlags)
    }

    @Test
    fun emoteSurvivesJsonRoundTrip() {
        val original =
            emote(
                modifierEffects = setOf(FfzEffect.FLIP_X, FfzEffect.RAINBOW),
                images =
                    listOf(
                        EmoteImage(1, "https://cdn.example/1x.webp"),
                        EmoteImage(2, "https://cdn.example/2x.webp"),
                    ),
            )
        assertTrue(original.isModifier)
        assertEquals(original, Json.decodeFromString<Emote>(Json.encodeToString(original)))
    }

    @Test
    fun highlightDefaultsAreTheOnRowsPlusMention() {
        assertEquals(
            setOf(
                HighlightType.TWITCH_SUB,
                HighlightType.TWITCH_ANNOUNCEMENT,
                HighlightType.KICK_SUB,
                HighlightType.KICKS_GIFT,
                HighlightType.KICK_HOST,
                HighlightType.TWITCH_CHEER,
                HighlightType.MENTION,
                HighlightType.KICK_PINNED,
            ),
            HighlightType.DEFAULTS,
        )
    }

    private fun emote(
        contentFlags: Int = 0,
        modifierEffects: Set<FfzEffect> = emptySet(),
        images: List<EmoteImage> = emptyList(),
    ) = Emote(
        id = "e1",
        name = "Name",
        provider = EmoteProvider.SEVENTV,
        scope = EmoteScope.GLOBAL,
        images = images,
        contentFlags = contentFlags,
        modifierEffects = modifierEffects,
    )
}

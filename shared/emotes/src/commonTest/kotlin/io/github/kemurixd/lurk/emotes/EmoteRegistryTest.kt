package io.github.kemurixd.lurk.emotes

import app.cash.turbine.test
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteProvider.BTTV
import io.github.kemurixd.lurk.core.model.EmoteProvider.FFZ
import io.github.kemurixd.lurk.core.model.EmoteProvider.KICK
import io.github.kemurixd.lurk.core.model.EmoteProvider.SEVENTV
import io.github.kemurixd.lurk.core.model.EmoteProvider.TWITCH
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.EmoteScope.CHANNEL
import io.github.kemurixd.lurk.core.model.EmoteScope.GLOBAL
import io.github.kemurixd.lurk.core.model.EmoteScope.PERSONAL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmoteRegistryTest {
    private fun emote(
        id: String,
        name: String,
        provider: EmoteProvider,
        scope: EmoteScope,
        hidden: Boolean = false,
    ) = Emote(
        id = id,
        name = name,
        provider = provider,
        scope = scope,
        images = listOf(EmoteImage(1, "https://example.invalid/$id/1x")),
        hiddenInPicker = hidden,
    )

    private fun set(
        provider: EmoteProvider,
        scope: EmoteScope,
        vararg emotes: Emote,
    ) = ProviderSet(provider, scope, emotes.toList())

    @Test
    fun priorityWalksDownFromChannelSevenTvToNative() {
        val registry = EmoteRegistry()
        val sources =
            listOf(
                Triple(SEVENTV, CHANNEL, "c7"),
                Triple(BTTV, CHANNEL, "cb"),
                Triple(FFZ, CHANNEL, "cf"),
                Triple(SEVENTV, GLOBAL, "g7"),
                Triple(BTTV, GLOBAL, "gb"),
                Triple(FFZ, GLOBAL, "gf"),
                Triple(TWITCH, GLOBAL, "nt"),
            )
        sources.forEach { (p, s, id) -> registry.put(set(p, s, emote(id, "Kappa", p, s))) }
        sources.forEach { (p, s, id) ->
            assertEquals(
                id,
                registry.snapshot.value
                    .find("Kappa")
                    ?.id,
            )
            registry.put(set(p, s))
        }
        assertNull(registry.snapshot.value.find("Kappa"))
    }

    @Test
    fun kickIsNativeToo() {
        val registry = EmoteRegistry()
        registry.put(set(KICK, CHANNEL, emote("k", "KEKW", KICK, CHANNEL)))
        registry.put(set(FFZ, GLOBAL, emote("gf", "KEKW", FFZ, GLOBAL)))
        assertEquals(
            "gf",
            registry.snapshot.value
                .find("KEKW")
                ?.id,
        )
    }

    @Test
    fun matchIsExactAndCaseSensitive() {
        val registry = EmoteRegistry()
        registry.put(set(BTTV, GLOBAL, emote("a", "Pog", BTTV, GLOBAL)))
        assertNull(registry.snapshot.value.find("pog"))
        assertEquals(
            "a",
            registry.snapshot.value
                .find("Pog")
                ?.id,
        )
    }

    @Test
    fun personalEmoteWinsOnlyForItsOwner() {
        val registry = EmoteRegistry()
        registry.put(set(SEVENTV, CHANNEL, emote("chan", "Wave", SEVENTV, CHANNEL)))
        registry.putPersonal("u1", listOf(emote("mine", "Wave", SEVENTV, PERSONAL)))
        val snap = registry.snapshot.value
        assertEquals("mine", snap.find("Wave", "u1")?.id)
        assertEquals("chan", snap.find("Wave", "u2")?.id)
        assertEquals("chan", snap.find("Wave")?.id)
        registry.removePersonal("u1")
        assertEquals(
            "chan",
            registry.snapshot.value
                .find("Wave", "u1")
                ?.id,
        )
    }

    @Test
    fun putReplacesTheProviderAndScope() {
        val registry = EmoteRegistry()
        registry.put(set(BTTV, CHANNEL, emote("old", "OnlyOld", BTTV, CHANNEL), emote("k", "Keep", BTTV, CHANNEL)))
        registry.put(set(BTTV, CHANNEL, emote("new", "OnlyNew", BTTV, CHANNEL)))
        val snap = registry.snapshot.value
        assertNull(snap.find("OnlyOld"))
        assertNull(snap.find("Keep"))
        assertEquals("new", snap.find("OnlyNew")?.id)
    }

    @Test
    fun addAndRemoveByIdBumpGeneration() {
        val registry = EmoteRegistry()
        val g0 = registry.snapshot.value.generation
        registry.addEmote(SEVENTV, CHANNEL, emote("x", "Live", SEVENTV, CHANNEL))
        val g1 = registry.snapshot.value.generation
        assertEquals(
            "x",
            registry.snapshot.value
                .find("Live")
                ?.id,
        )
        registry.removeEmote(SEVENTV, CHANNEL, "x")
        val g2 = registry.snapshot.value.generation
        assertNull(registry.snapshot.value.find("Live"))
        assertTrue(g0 < g1 && g1 < g2)
    }

    @Test
    fun pickerSkipsHiddenAndPersonalAndSortsIgnoringCase() {
        val registry = EmoteRegistry()
        registry.put(
            set(
                SEVENTV,
                CHANNEL,
                emote("1", "banana", SEVENTV, CHANNEL),
                emote("2", "Apple", SEVENTV, CHANNEL),
                emote("3", "cherry", SEVENTV, CHANNEL),
                emote("h", "Secret", SEVENTV, CHANNEL, hidden = true),
            ),
        )
        registry.putPersonal("u1", listOf(emote("p", "Mine", SEVENTV, PERSONAL)))
        val snap = registry.snapshot.value
        assertEquals(listOf("Apple", "banana", "cherry"), snap.picker().getValue(SEVENTV).map { it.name })
        assertEquals("h", snap.find("Secret")?.id)
        assertNull(snap.find("Mine"))
        assertEquals("p", snap.find("Mine", "u1")?.id)
    }

    @Test
    fun noOpMutationDoesNotBumpGeneration() {
        val registry = EmoteRegistry()
        val a = emote("a", "A", BTTV, CHANNEL)
        registry.put(set(BTTV, CHANNEL, a))
        registry.putPersonal("u1", listOf(emote("p", "P", SEVENTV, PERSONAL)))
        val before = registry.snapshot.value
        registry.removeEmote(BTTV, CHANNEL, "unknown")
        registry.removeEmote(FFZ, GLOBAL, "unknown")
        registry.removePersonal("nobody")
        registry.put(set(BTTV, CHANNEL, a))
        registry.putPersonal("u1", listOf(emote("p", "P", SEVENTV, PERSONAL)))
        assertTrue(registry.snapshot.value === before)
    }

    @Test
    fun snapshotEmitsOncePerMutation() =
        runTest {
            val registry = EmoteRegistry()
            registry.snapshot.test {
                assertEquals(EmoteSnapshot.EMPTY.generation, awaitItem().generation)
                registry.put(set(BTTV, GLOBAL, emote("a", "A", BTTV, GLOBAL)))
                val g1 = awaitItem().generation
                registry.addEmote(BTTV, GLOBAL, emote("b", "B", BTTV, GLOBAL))
                val g2 = awaitItem().generation
                registry.removeEmote(BTTV, GLOBAL, "a")
                val g3 = awaitItem().generation
                assertTrue(g1 < g2 && g2 < g3)
                expectNoEvents()
            }
        }
}

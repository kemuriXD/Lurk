package io.github.kemurixd.lurk.emotes.seventv

import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.Paint
import io.github.kemurixd.lurk.core.model.PaintFunction
import io.github.kemurixd.lurk.emotes.seventv.SevenTvChange.BadgeDefined
import io.github.kemurixd.lurk.emotes.seventv.SevenTvChange.Entitled
import io.github.kemurixd.lurk.emotes.seventv.SevenTvChange.PaintDefined
import io.github.kemurixd.lurk.emotes.seventv.SevenTvChange.Unentitled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CosmeticsRegistryTest {
    private val paint = Paint("p1", "Sunrise", PaintFunction.LINEAR_GRADIENT)
    private val other = Paint("p2", "Dusk", PaintFunction.RADIAL_GRADIENT)
    private val badge = Badge("b1", "sub15", "Subscriber", "https://cdn.7tv.app/badge/b1/4x.webp")
    private val registry = CosmeticsRegistry()

    private fun paintFor(user: String) = registry.state.value.paintFor(user)

    private fun badgeFor(user: String) = registry.state.value.badgeFor(user)

    @Test
    fun definedAndEntitledPaintResolves() {
        registry.apply(PaintDefined(paint))
        registry.apply(Entitled("PAINT", "p1", "u1"))
        assertEquals(paint, paintFor("u1"))
        assertNull(paintFor("u2"))
    }

    @Test
    fun unentitledPaintClears() {
        registry.apply(PaintDefined(paint))
        registry.apply(Entitled("PAINT", "p1", "u1"))
        registry.apply(Unentitled("PAINT", "p1", "u1"))
        assertNull(paintFor("u1"))
    }

    @Test
    fun entitlementBeforePaintResolvesLater() {
        registry.apply(Entitled("PAINT", "p1", "u1"))
        assertNull(paintFor("u1"))
        registry.apply(PaintDefined(paint))
        assertEquals(paint, paintFor("u1"))
    }

    @Test
    fun staleUnentitleKeepsNewerPaint() {
        registry.apply(PaintDefined(paint))
        registry.apply(PaintDefined(other))
        registry.apply(Entitled("PAINT", "p1", "u1"))
        registry.apply(Entitled("PAINT", "p2", "u1"))
        registry.apply(Unentitled("PAINT", "p1", "u1"))
        assertEquals(other, paintFor("u1"))
    }

    @Test
    fun badgesFollowEntitlements() {
        registry.apply(Entitled("BADGE", "b1", "u1"))
        registry.apply(BadgeDefined("b1", badge))
        assertEquals(badge, badgeFor("u1"))
        assertNull(paintFor("u1"))
        registry.apply(Unentitled("BADGE", "b1", "u1"))
        assertNull(badgeFor("u1"))
    }

    @Test
    fun emoteSetEntitlementsAreIgnored() {
        val before = registry.state.value
        registry.apply(Entitled("EMOTE_SET", "s1", "u1"))
        assertEquals(before, registry.state.value)
    }
}

package io.github.kemurixd.lurk.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsThatFitTest {
    private val more = { _: Int -> 30 }

    @Test
    fun everyTagShowsWhenTheyAllFit() {
        assertEquals(3, tagsThatFit(listOf(30, 30, 30), available = 98, gap = 4, moreWidth = more))
    }

    @Test
    fun overflowKeepsRoomForTheMoreChip() {
        // Two tags fit on their own (104), but not with the "+1" chip after them (138), so one tag and "+2" show.
        assertEquals(1, tagsThatFit(listOf(50, 50, 50), available = 120, gap = 4, moreWidth = more))
    }

    @Test
    fun aWiderMoreChipCanPushOutOneMoreTag() {
        val wide = { hidden: Int -> if (hidden >= 2) 40 else 20 }
        assertEquals(2, tagsThatFit(listOf(40, 40, 40), available = 110, gap = 4, moreWidth = wide))
    }

    @Test
    fun noTagsFitWhenEvenTheFirstLeavesNoRoomForTheMoreChip() {
        assertEquals(0, tagsThatFit(listOf(100, 10), available = 110, gap = 4, moreWidth = more))
    }

    @Test
    fun noTagsMeansNothingToShow() {
        assertEquals(0, tagsThatFit(emptyList(), available = 100, gap = 4, moreWidth = more))
    }
}

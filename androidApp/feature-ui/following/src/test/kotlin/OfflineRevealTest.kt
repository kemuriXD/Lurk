package io.github.kemurixd.lurk.featureui.following

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRevealTest {
    @Test
    fun aFullRowAlreadyInViewNeedsNoScroll() {
        assertEquals(0, offlineRevealScroll(headerTop = 300, headerHeight = 48, rowHeight = 72, viewportEnd = 1_000))
    }

    @Test
    fun aRowExactlyFittingNeedsNoScroll() {
        assertEquals(0, offlineRevealScroll(headerTop = 880, headerHeight = 48, rowHeight = 72, viewportEnd = 1_000))
    }

    @Test
    fun aHalfCutRowScrollsByTheCutPart() {
        // Header ends at 948; the row would run to 1,020 and 20 px hang below a 1,000 px viewport.
        assertEquals(20, offlineRevealScroll(headerTop = 900, headerHeight = 48, rowHeight = 72, viewportEnd = 1_000))
    }

    @Test
    fun aHeaderAtTheVeryBottomScrollsByAWholeRow() {
        assertEquals(72, offlineRevealScroll(headerTop = 952, headerHeight = 48, rowHeight = 72, viewportEnd = 1_000))
    }

    @Test
    fun theHeaderIsNeverScrolledOutOfView() {
        // Only 100 px of room: the header and a row can't both fit, so the header goes to the top and stops.
        assertEquals(40, offlineRevealScroll(headerTop = 40, headerHeight = 48, rowHeight = 72, viewportEnd = 100))
    }

    @Test
    fun aHeaderAlreadyAtOrAboveTheTopNeverScrolls() {
        assertEquals(0, offlineRevealScroll(headerTop = -10, headerHeight = 48, rowHeight = 72, viewportEnd = 100))
    }
}

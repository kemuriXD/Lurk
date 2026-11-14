package io.github.kemurixd.lurk.featureui.following

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRevealTest {
    private fun scroll(
        headerTop: Int,
        viewportEnd: Int,
        rows: Int = 5,
        headerHeight: Int = 48,
        rowHeight: Int = 72,
    ) = offlineRevealScroll(headerTop, headerHeight, rowHeight, rows, viewportEnd)

    @Test
    fun allTheRowsInViewNeedNoScroll() {
        // Header, then 3 rows of 72 end at 564, well above the edge: nothing is cut and nothing peeks.
        assertEquals(0, scroll(headerTop = 300, viewportEnd = 1_000, rows = 3))
    }

    @Test
    fun aRowExactlyFittingNeedsNoScroll() {
        assertEquals(0, scroll(headerTop = 880, viewportEnd = 1_000))
    }

    @Test
    fun aHalfCutRowScrollsByTheCutPart() {
        // Header ends at 948; the row would run to 1,020 and 20 px hang below a 1,000 px viewport.
        assertEquals(20, scroll(headerTop = 900, viewportEnd = 1_000))
    }

    @Test
    fun aHeaderAtTheVeryBottomScrollsByAWholeRow() {
        assertEquals(72, scroll(headerTop = 952, viewportEnd = 1_000))
    }

    @Test
    fun theHeaderIsNeverScrolledOutOfView() {
        // Only 100 px of room: the header and a row can't both fit, so the header goes to the top and stops.
        assertEquals(40, scroll(headerTop = 40, viewportEnd = 100))
    }

    @Test
    fun aHeaderAlreadyAtOrAboveTheTopNeverScrolls() {
        assertEquals(0, scroll(headerTop = -10, viewportEnd = 100))
    }

    @Test
    fun aPartialNextRowIsScrolledBackOutOfView() {
        // Rows end at 420 and 492; the edge at 450 would show 30 px of the second row, so move the list down 30.
        assertEquals(-30, scroll(headerTop = 300, viewportEnd = 450))
    }

    @Test
    fun aSecondRowThatFitsWholeNeedsNoScroll() {
        assertEquals(0, scroll(headerTop = 300, viewportEnd = 492))
    }

    @Test
    fun aPartialThirdRowIsAlsoScrolledBack() {
        assertEquals(-10, scroll(headerTop = 300, viewportEnd = 502))
    }

    @Test
    fun withNoRowBeyondTheLastNothingPeeks() {
        // One offline row only: the space under it is empty, so there is nothing to hide.
        assertEquals(0, scroll(headerTop = 300, viewportEnd = 450, rows = 1))
    }

    @Test
    fun theBottomPaddingBandCountsAsVisibleBecauseRowsDrawInIt() {
        // The list ends at 1,016 including its 16 px bottom padding. A row ending at 1,000 would let the next row's top
        // show in the band, so the row must end on the real edge, 1,016.
        assertEquals(-16, scroll(headerTop = 880, viewportEnd = 1_016))
        assertEquals(0, scroll(headerTop = 896, viewportEnd = 1_016))
    }
}

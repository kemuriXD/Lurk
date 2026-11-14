package io.github.kemurixd.lurk.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatViewersTest {
    @Test
    fun formatsThousandsAndMillions() {
        assertEquals("0", formatViewers(0))
        assertEquals("999", formatViewers(999))
        assertEquals("1K", formatViewers(1_000))
        assertEquals("12.4K", formatViewers(12_400))
        assertEquals("21.7K", formatViewers(21_650))
        assertEquals("1M", formatViewers(999_999))
        assertEquals("1.5M", formatViewers(1_500_000))
    }

    @Test
    fun fullCountUsesTheLocaleSeparators() {
        assertEquals("20,052", formatViewerCount(20_052, Locale.US))
        assertEquals("20.052", formatViewerCount(20_052, Locale.GERMANY))
        assertEquals("999", formatViewerCount(999, Locale.US))
        assertEquals("1,234,567", formatViewerCount(1_234_567, Locale.US))
    }
}

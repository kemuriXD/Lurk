package io.github.kemurixd.lurk.playback.hls

import kotlin.test.Test
import kotlin.test.assertEquals

class HlsAttributesTest {
    @Test
    fun quotedValuesKeepCommasAndLoseQuotes() {
        val attrs = parseAttributes("""ID="a,b",CLASS="twitch-maf-ad",PLANNED-DURATION=60.000""")
        assertEquals(setOf("ID", "CLASS", "PLANNED-DURATION"), attrs.keys)
        assertEquals("a,b", attrs["ID"])
        assertEquals("twitch-maf-ad", attrs["CLASS"])
        assertEquals("60.000", attrs["PLANNED-DURATION"])
    }

    @Test
    fun malformedPairsAreSkipped() {
        val attrs = parseAttributes("""JUNK,A=1,=2,B="x",C""")
        assertEquals(mapOf("A" to "1", "B" to "x"), attrs)
    }

    @Test
    fun unterminatedQuoteDoesNotThrow() {
        assertEquals("1", parseAttributes("""A=1,B="never closed""")["A"])
    }
}

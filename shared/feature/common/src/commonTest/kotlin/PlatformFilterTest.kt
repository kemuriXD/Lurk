package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformFilterTest {
    @Test
    fun allIncludesEveryPlatform() {
        Platform.entries.forEach { assertTrue(PlatformFilter.ALL.includes(it)) }
    }

    @Test
    fun singlePlatformFiltersIncludeOnlyTheirOwn() {
        assertTrue(PlatformFilter.TWITCH.includes(Platform.TWITCH))
        assertFalse(PlatformFilter.TWITCH.includes(Platform.KICK))
        assertTrue(PlatformFilter.KICK.includes(Platform.KICK))
        assertFalse(PlatformFilter.KICK.includes(Platform.TWITCH))
    }
}

package io.github.kemurixd.lurk.designsystem.component

import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformLabelTest {
    @Test
    fun eachPlatformHasItsNameString() {
        assertEquals(R.string.platform_twitch, Platform.TWITCH.labelRes)
        assertEquals(R.string.platform_kick, Platform.KICK.labelRes)
    }
}

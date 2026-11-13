package io.github.kemurixd.lurk.designsystem.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK 36 and 37 need Java 21 under Robolectric; the build runs on 17, so default to 35.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LurkThemeTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun ColorScheme.blackSurfaces() = listOf(background, surface, surfaceDim, surfaceContainerLowest)

    private fun ColorScheme.amoledContainers() =
        listOf(surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)

    private val figmaAmoledContainers =
        listOf(Color(0xFF061011), Color(0xFF0D1516), Color(0xFF171D1E), Color(0xFF1A2122))

    @Test
    fun amoledSurfacesArePureBlack() {
        val scheme = lurkColorScheme(dark = true, amoled = true)
        scheme.blackSurfaces().forEach { assertEquals(Color.Black, it) }
        assertEquals(figmaAmoledContainers, scheme.amoledContainers())
    }

    @Test
    fun amoledIsIgnoredInLightMode() {
        assertEquals(lurkColorScheme(dark = false, amoled = false), lurkColorScheme(dark = false, amoled = true))
    }

    @Test
    @Config(sdk = [31])
    fun dynamicColorOnApi31() {
        val dynamic = resolveColorScheme(context, dark = true, amoled = false, dynamicColor = true)
        assertNotEquals(lurkColorScheme(dark = true, amoled = false), dynamic)
    }

    @Test
    @Config(sdk = [30])
    fun dynamicColorFallsBackToStaticBelowApi31() {
        val scheme = resolveColorScheme(context, dark = true, amoled = false, dynamicColor = true)
        assertEquals(lurkColorScheme(dark = true, amoled = false), scheme)
    }

    @Test
    @Config(sdk = [31])
    fun amoledAppliesOverDynamic() {
        val scheme = resolveColorScheme(context, dark = true, amoled = true, dynamicColor = true)
        scheme.blackSurfaces().forEach { assertEquals(Color.Black, it) }
        assertEquals(figmaAmoledContainers, scheme.amoledContainers())
        assertNotEquals(lurkColorScheme(dark = true, amoled = true).primary, scheme.primary)
        assertDerivedRoles("dynamic AMOLED", scheme)
    }

    @Test
    @Config(sdk = [30])
    fun themeProvidesSchemeAndLocals() {
        var scheme: ColorScheme? = null
        var spacing: LurkSpacing? = null
        var platform: PlatformColors? = null
        compose.setContent {
            LurkTheme(mode = ThemeMode.DARK, amoled = true, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
                spacing = LurkTheme.spacing
                platform = LurkTheme.platformColors
            }
        }
        compose.waitForIdle()
        assertEquals(lurkColorScheme(dark = true, amoled = true), scheme)
        assertEquals(LurkSpacing(), spacing)
        assertEquals(PlatformColors(), platform)
    }
}

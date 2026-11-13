package io.github.kemurixd.lurk.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TokensMatchFigmaTest {
    private val tokens: JsonObject =
        Json
            .parseToJsonElement(
                checkNotNull(javaClass.classLoader?.getResourceAsStream("figma-tokens.json")) {
                    "figma-tokens.json missing"
                }.bufferedReader().readText(),
            ).jsonObject

    private val getters: Map<String, (ColorScheme) -> Color> =
        mapOf(
            "primary" to { it.primary },
            "onPrimary" to { it.onPrimary },
            "primaryContainer" to { it.primaryContainer },
            "onPrimaryContainer" to { it.onPrimaryContainer },
            "inversePrimary" to { it.inversePrimary },
            "secondary" to { it.secondary },
            "onSecondary" to { it.onSecondary },
            "secondaryContainer" to { it.secondaryContainer },
            "onSecondaryContainer" to { it.onSecondaryContainer },
            "tertiary" to { it.tertiary },
            "onTertiary" to { it.onTertiary },
            "tertiaryContainer" to { it.tertiaryContainer },
            "onTertiaryContainer" to { it.onTertiaryContainer },
            "error" to { it.error },
            "onError" to { it.onError },
            "errorContainer" to { it.errorContainer },
            "onErrorContainer" to { it.onErrorContainer },
            "surface" to { it.surface },
            "onSurface" to { it.onSurface },
            "onSurfaceVariant" to { it.onSurfaceVariant },
            "surfaceDim" to { it.surfaceDim },
            "surfaceBright" to { it.surfaceBright },
            "surfaceContainerLowest" to { it.surfaceContainerLowest },
            "surfaceContainerLow" to { it.surfaceContainerLow },
            "surfaceContainer" to { it.surfaceContainer },
            "surfaceContainerHigh" to { it.surfaceContainerHigh },
            "surfaceContainerHighest" to { it.surfaceContainerHighest },
            "inverseSurface" to { it.inverseSurface },
            "inverseOnSurface" to { it.inverseOnSurface },
            "outline" to { it.outline },
            "outlineVariant" to { it.outlineVariant },
            "scrim" to { it.scrim },
        )

    /** Roles the theme deliberately forces to pure black on AMOLED, over the Figma value. */
    private val amoledBlackOverrides =
        setOf("surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest")

    private fun section(name: String) = tokens.getValue(name).jsonObject

    private fun hex(value: String): Color {
        val digits = value.removePrefix("#")
        val argb =
            when (digits.length) {
                6 -> 0xFF000000L or digits.toLong(16)
                8 -> (digits.substring(6).toLong(16) shl 24) or digits.substring(0, 6).toLong(16)
                else -> error("bad colour $value")
            }
        return Color(argb.toInt())
    }

    @Test
    fun everyColorRoleMatches() {
        val modes = section("color")
        assertEquals(setOf("Light", "Dark", "AMOLED"), modes.keys)
        for ((mode, roles) in modes) {
            val scheme = lurkColorScheme(dark = mode != "Light", amoled = mode == "AMOLED")
            var checked = 0
            for ((role, value) in roles.jsonObject) {
                val expected = hex(value.jsonPrimitive.content)
                when {
                    role == "shadow" -> {
                        assertEquals("$mode/$role", expected, LurkShadow)
                    }

                    role.startsWith("player/") -> {
                        Unit
                    }

                    else -> {
                        val getter = getters[role] ?: error("no ColorScheme getter for $role")
                        val pinnedBlack = mode == "AMOLED" && role in amoledBlackOverrides
                        assertEquals("$mode/$role", if (pinnedBlack) Color.Black else expected, getter(scheme))
                        checked++
                    }
                }
            }
            assertEquals("$mode role count", getters.size, checked)
        }
    }

    @Test
    fun playerColorsMatch() {
        for ((mode, roles) in section("color")) {
            val r = roles.jsonObject
            assertEquals(mode, hex(r.getValue("player/scrim").jsonPrimitive.content), PlayerColors.scrim)
            assertEquals(mode, hex(r.getValue("player/onScrim").jsonPrimitive.content), PlayerColors.onScrim)
        }
    }

    @Test
    fun everySpaceAndRadiusMatches() {
        val space = section("space").mapValues { (it.value as JsonPrimitive).int }
        val spacing = LurkSpacing()
        val expectedSpace =
            mapOf(
                "space/0" to spacing.none,
                "space/2" to spacing.xxs,
                "space/4" to spacing.xs,
                "space/8" to spacing.sm,
                "space/12" to spacing.md,
                "space/16" to spacing.lg,
                "space/20" to spacing.xl,
                "space/24" to spacing.xxl,
                "space/32" to spacing.xxxl,
            )
        assertEquals(space.keys, expectedSpace.keys)
        for ((name, dp) in expectedSpace) assertEquals(name, space.getValue(name).toFloat(), dp.value, 0f)

        val radius = section("radius").mapValues { (it.value as JsonPrimitive).int }
        val expectedRadius =
            mapOf(
                "radius/none" to LurkRadius.none,
                "radius/xs" to LurkRadius.xs,
                "radius/sm" to LurkRadius.sm,
                "radius/md" to LurkRadius.md,
                "radius/lg" to LurkRadius.lg,
                "radius/xl" to LurkRadius.xl,
                "radius/full" to LurkRadius.full,
            )
        assertEquals(radius.keys, expectedRadius.keys)
        for ((name, dp) in expectedRadius) assertEquals(name, radius.getValue(name).toFloat(), dp.value, 0f)
    }

    @Test
    fun platformAndHighlightColorsMatch() {
        val platform = section("platform").mapValues { hex(it.value.jsonPrimitive.content) }
        val p = PlatformColors()
        assertEquals(
            mapOf("twitch" to p.twitch, "onTwitch" to p.onTwitch, "kick" to p.kick, "onKick" to p.onKick),
            platform,
        )
        val highlight = section("highlight").mapValues { hex(it.value.jsonPrimitive.content) }
        val h = HighlightColors()
        assertEquals(
            mapOf(
                "sub" to h.sub,
                "announcement" to h.announcement,
                "firstMessage" to h.firstMessage,
                "highlighted" to h.highlighted,
                "gift" to h.gift,
                "mention" to h.mention,
                "cheer" to h.cheer,
                "raid" to h.raid,
            ),
            highlight,
        )
    }
}

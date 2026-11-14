package io.github.kemurixd.lurk.featureui.settings

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.theme.ThemeMode
import io.github.kemurixd.lurk.designsystem.time.LocalClock
import kotlin.time.Clock
import kotlin.time.Instant

/** The phone the baselines are drawn on: 360 x 780 dp at 1.5x. Every snapshot test class uses it. */
internal const val SNAPSHOT_DEVICE = "w360dp-h780dp-hdpi"

/** The same phone at the length Figma draws "full length" frames: the whole list, Offline section included. */
internal const val SNAPSHOT_TALL_DEVICE = "w360dp-h1140dp-hdpi"

/** The one time every snapshot shows, so "3 days ago" never changes. */
private val SnapshotNow = Instant.parse("2026-11-13T12:00:00Z")

private val SnapshotClock =
    object : Clock {
        override fun now(): Instant = SnapshotNow
    }

/**
 * A theme and type scale a baseline is drawn in. The names follow Figma: a frame is `Area / Screen / State / Theme`,
 * and the 200% frames are Dark.
 */
internal enum class Look(
    val label: String,
    val dark: Boolean,
    val amoled: Boolean,
    val fontScale: Float = 1f,
) {
    Light("Light", dark = false, amoled = false),
    Dark("Dark", dark = true, amoled = false),
    Amoled("AMOLED", dark = true, amoled = true),
    LargeFont("LargeFont200_Dark", dark = true, amoled = false, fontScale = 2f),
}

/** Virtual time each look is given to settle, the same for every capture. */
private const val SETTLE_MILLIS = 600L

private val ThemeLooks = listOf(Look.Light, Look.Dark, Look.Amoled)

/**
&, Dark and AMOLED (and Dark at 200% type when [largeFont]) and compares each with its
 * baseline `src/test/snapshots/<name>_<look>.png`.
 * [prepare] runs once the content is up and before the first capture, for taps that reach the state to show.
 *
 * Everything that could change between runs is pinned: the clock, the system animation scale (so the live dot is
 * still), the colours (no wallpaper) and the font scale. Renders can differ a little between Windows and Linux in
 * anti-aliasing, so each pixel may be off by a hair and 0.5% of them may differ before a check fails.
 */
internal fun ComposeContentTestRule.snapshots(
    name: String,
    largeFont: Boolean = false,
    prepare: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    var look by mutableStateOf(Look.Light)
    setContent {
        val resolver = LocalContext.current.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, look.fontScale),
            LocalClock provides SnapshotClock,
        ) {
            LurkTheme(
                mode = if (look.dark) ThemeMode.DARK else ThemeMode.LIGHT,
                amoled = look.amoled,
                dynamicColor = false,
            ) {
                ErrorHost(ErrorHandlers()) {
                    // A Surface forces its content to its own size; a Column lets the content keep the size it has.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
    }
    prepare()
    val looks = if (largeFont) ThemeLooks + Look.LargeFont else ThemeLooks
    for (next in looks) {
        runOnIdle { look = next }
        waitForIdle()
        // Lets what waits a moment (the Retry spinner) show, and the animations that follow a theme change finish.
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        onRoot().captureRoboImage("src/test/snapshots/${name}_${next.label}.png", roborazziOptions = SnapshotOptions)
    }
}

private val SnapshotOptions =
    RoborazziOptions(
        compareOptions =
            RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = 0.01f),
                resultValidator = ThresholdValidator(0.005f),
            ),
    )

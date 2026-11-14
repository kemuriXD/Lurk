package io.github.kemurixd.lurk.designsystem.snapshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.dropbox.differ.Image
import com.dropbox.differ.Mask
import io.github.kemurixd.lurk.designsystem.SNAPSHOT_DEVICE
import io.github.kemurixd.lurk.designsystem.SnapshotOptions
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tolerance of the screenshot check is tight enough to notice a small design slip: a 4 dp change of padding on a
 * small component, or one colour token swapped for another, is more than the allowed 0.1% of pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class SnapshotBudgetTest {
    @get:Rule val rule = createComposeRule()

    private var padding by mutableStateOf(8.dp)
    private var container by mutableStateOf(Color.Unspecified)

    private fun show() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                val colors =
                    if (container == Color.Unspecified) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(containerColor = container)
                    }
                Column(
                    Modifier.fillMaxSize().padding(padding),
                ) { Button(onClick = {}, colors = colors) { Text("Retry") } }
            }
        }
    }

    private class Pixels(
        private val bitmap: ImageBitmap,
    ) : Image {
        private val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
        override val width get() = bitmap.width
        override val height get() = bitmap.height

        override fun getPixel(
            x: Int,
            y: Int,
        ): com.dropbox.differ.Color {
            val argb = pixels[y * width + x]
            return com.dropbox.differ.Color(
                ((argb shr 16) and 0xFF) / 255f,
                ((argb shr 8) and 0xFF) / 255f,
                (argb and 0xFF) / 255f,
                ((argb ushr 24) and 0xFF) / 255f,
            )
        }
    }

    private fun capture(): Image = Pixels(rule.onRoot().captureToImage())

    /** Whether the real check would accept [changed] against [baseline]. */
    private fun passes(
        baseline: Image,
        changed: Image,
    ): Boolean {
        val options = SnapshotOptions.compareOptions
        val result =
            options.imageComparator.compare(
                baseline,
                changed,
                Mask(
                    baseline.width,
                    baseline.height,
                    FloatArray(baseline.width * baseline.height),
                ),
            )
        return options.resultValidator(result)
    }

    @Test
    fun anUnchangedRenderPasses() {
        show()
        val first = capture()
        rule.waitForIdle()
        assertTrue(passes(first, capture()))
    }

    @Test
    fun fourDpOfPaddingOnASmallComponentFails() {
        show()
        val before = capture()
        padding = 12.dp
        rule.waitForIdle()
        assertFalse("a 4 dp shift went unnoticed", passes(before, capture()))
    }

    @Test
    fun aSwappedColourTokenFails() {
        show()
        val before = capture()
        container = Color(0xFF8E4585)
        rule.waitForIdle()
        assertFalse("a colour swap went unnoticed", passes(before, capture()))
    }
}

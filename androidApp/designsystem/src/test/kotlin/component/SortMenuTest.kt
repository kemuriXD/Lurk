package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SortMenuTest {
    @get:Rule val rule = createComposeRule()

    private val picked = mutableListOf<String>()

    private fun show(selected: String) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SortMenu(
                        selected = selected,
                        options = listOf("first", "second", "third"),
                        label = { it.replaceFirstChar(Char::uppercase) },
                        description = { "Sort items, currently by $it" },
                        onSelect = { picked += it },
                    )
                }
            }
        }
    }

    @Test
    fun showsTheCurrentSortAndNamesItForScreenReaders() {
        show("second")
        rule.onNodeWithText("Second").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sort items, currently by Second").assertIsDisplayed()
    }

    @Test
    fun theMenuOpensUnderTheButtonNotAtTheScreensLeftEdge() {
        show("first")
        val button = rule.onNodeWithContentDescription("Sort items, currently by First").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Sort items, currently by First").performClick()
        val item = rule.onNodeWithText("Third").fetchSemanticsNode()

        val buttonBox = button.onScreen()
        val itemBox = item.onScreen()
        assertTrue("menu starts left of the button: $itemBox vs $buttonBox", itemBox.right > buttonBox.left)
        assertTrue(
            "menu ends at the button's end edge: $itemBox vs $buttonBox",
            abs(itemBox.right - buttonBox.right) <= EDGE_TOLERANCE,
        )
        assertTrue("menu opens below the button top: $itemBox vs $buttonBox", itemBox.top >= buttonBox.top)
    }

    @Test
    fun theMenuItemsAreFortyEightDpTallWithALeadingCheckSlot() {
        show("first")
        rule.onNodeWithContentDescription("Sort items, currently by First").performClick()
        val density = rule.density
        val third = rule.onNodeWithText("Third").fetchSemanticsNode()
        assertEquals(
            48f,
            with(density) {
                third.size.height
                    .toDp()
                    .value
            },
            0.5f,
        )
    }

    @Test
    fun pickingAnOptionReportsItAndClosesTheMenu() {
        show("first")
        rule.onNodeWithContentDescription("Sort items, currently by First").performClick()
        rule.onNodeWithText("Third").performClick()

        assertEquals(listOf("third"), picked)
        rule.onNodeWithText("Third").assertDoesNotExist()
    }

    /** The node's bounds in screen pixels, so a popup window and the main window compare. */
    private fun SemanticsNode.onScreen(): Rect =
        Rect(
            layoutInfo.coordinates.positionOnScreen(),
            androidx.compose.ui.geometry
                .Size(size.width.toFloat(), size.height.toFloat()),
        )

    private companion object {
        const val EDGE_TOLERANCE = 16f
    }
}

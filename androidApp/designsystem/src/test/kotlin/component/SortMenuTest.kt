package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SortMenuTest {
    @get:Rule val rule = createComposeRule()

    private val picked = mutableListOf<String>()

    private fun show(selected: String) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                SortMenu(
                    selected = selected,
                    options = listOf("first", "second", "third"),
                    label = { it.replaceFirstChar(Char::uppercase) },
                    onSelect = { picked += it },
                )
            }
        }
    }

    @Test
    fun showsTheCurrentSortAndNamesItForScreenReaders() {
        show("second")
        rule.onNodeWithText("Second").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sort live channels, currently by Second").assertIsDisplayed()
    }

    @Test
    fun pickingAnOptionReportsItAndClosesTheMenu() {
        show("first")
        rule.onNodeWithContentDescription("Sort live channels, currently by First").performClick()
        rule.onNodeWithText("Third").performClick()

        assertEquals(listOf("third"), picked)
        rule.onNodeWithText("Third").assertDoesNotExist()
    }
}

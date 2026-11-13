package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SectionHeaderTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun sectionHeaderShowsCountBesideTitle() {
        rule.setContent { LurkTheme(dynamicColor = false) { SectionHeader("Live", count = 5) } }
        rule.onNodeWithTag(SectionHeaderTags.COUNT).assertTextEquals("5")
        val title = rule.onNodeWithText("Live").fetchSemanticsNode().boundsInRoot
        val count = rule.onNodeWithTag(SectionHeaderTags.COUNT).fetchSemanticsNode().boundsInRoot
        assertTrue("count starts after the title ends", count.left >= title.right)
        assertTrue("count sits right beside the title", count.left - title.right < 24f * 4)
        assertEquals(title.center.y, count.center.y, 4f)
    }
}

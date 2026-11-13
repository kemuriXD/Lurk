package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        lateinit var density: Density
        rule.setContent {
            density = LocalDensity.current
            LurkTheme(dynamicColor = false) { SectionHeader("Live", count = 5) }
        }
        rule.onNodeWithTag(SectionHeaderTags.COUNT, useUnmergedTree = true).assertTextEquals("5")
        val title = rule.onNodeWithText("Live", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val count =
            rule
                .onNodeWithTag(
                    SectionHeaderTags.COUNT,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue("count starts after the title ends", count.left >= title.right)
        assertTrue("count sits right beside the title", count.left - title.right < with(density) { 24.dp.toPx() })
        assertEquals(title.center.y, count.center.y, 4f)
    }

    @Test
    fun titleAndCountAreOneHeadingStop() {
        rule.setContent { LurkTheme(dynamicColor = false) { SectionHeader("Live", count = 5) } }
        val headings = rule.onAllNodes(isHeading())
        headings.assertCountEquals(1)
        val text = headings[0].fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue("merged heading reads title and count, was $text", "Live" in text && "5" in text)
    }

    @Test
    fun trailingSitsAtTheEndEdge() {
        lateinit var density: Density
        rule.setContent {
            density = LocalDensity.current
            LurkTheme(dynamicColor = false) {
                SectionHeader("Live", count = 5, modifier = Modifier.testTag("header")) {
                    Box(Modifier.size(40.dp).testTag("trailing"))
                }
            }
        }
        val header = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val trailing = rule.onNodeWithTag("trailing").fetchSemanticsNode().boundsInRoot
        assertEquals(header.right - with(density) { 8.dp.toPx() }, trailing.right, 1f)
    }

    @Test
    fun longTitleEllipsizesBeforeTrailing() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                SectionHeader("A very long section title ".repeat(12), modifier = Modifier.testTag("header")) {
                    Box(Modifier.size(40.dp).testTag("trailing"))
                }
            }
        }
        val title =
            rule
                .onNodeWithText(
                    "A very long",
                    substring = true,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val trailing = rule.onNodeWithTag("trailing").fetchSemanticsNode().boundsInRoot
        assertTrue("title ends before trailing starts", title.right <= trailing.left)
        val header = rule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        assertTrue("trailing stays inside the header", trailing.right <= header.right)
    }
}

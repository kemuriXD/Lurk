package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp")
class CollapsibleHeaderTest {
    @get:Rule val rule = createComposeRule()

    private fun show(collapsible: Boolean) {
        rule.setContent {
            CollapsibleHeader(
                collapsible = collapsible,
                header = { Box(Modifier.fillMaxWidth().height(120.dp).testTag("header")) { Text("Header") } },
            ) {
                LazyColumn(Modifier.weight(1f).testTag("list")) {
                    items((1..40).toList()) { Text("Row $it", Modifier.height(48.dp)) }
                }
            }
        }
    }

    @Test
    fun theHeaderSlidesAwayWhenTheListScrollsUpAndBackWhenItScrollsDown() {
        show(collapsible = true)
        rule.onNodeWithTag("header").assertIsDisplayed()
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("header").assertIsNotDisplayed()
        rule.onNodeWithTag("list").performTouchInput { swipeDown() }
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    @Test
    fun aHeaderThatIsNotCollapsibleStaysPut() {
        show(collapsible = false)
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    private var collapsible by mutableStateOf(true)
    private var canScroll by mutableStateOf(true)
    private var resetKey by mutableIntStateOf(0)

    private fun showWithFlags() {
        rule.setContent {
            CollapsibleHeader(
                collapsible = collapsible,
                canScroll = canScroll,
                resetKey = resetKey,
                header = { Box(Modifier.fillMaxWidth().height(120.dp).testTag("header")) { Text("Header") } },
            ) {
                LazyColumn(Modifier.weight(1f).testTag("list")) {
                    items((1..40).toList()) { Text("Row $it", Modifier.height(48.dp)) }
                }
            }
        }
    }

    private fun collapse() {
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("header").assertIsNotDisplayed()
    }

    @Test
    fun aCollapsedHeaderComesBackWhenTheContentStopsScrolling() {
        showWithFlags()
        collapse()
        canScroll = false
        rule.waitForIdle()
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    @Test
    fun aCollapsedHeaderComesBackWhenTheWindowIsNoLongerCompactAndStaysBackAfterwards() {
        showWithFlags()
        collapse()
        collapsible = false
        rule.waitForIdle()
        rule.onNodeWithTag("header").assertIsDisplayed()
        collapsible = true
        rule.waitForIdle()
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    @Test
    fun aCollapsedHeaderComesBackWhenTheKeyChanges() {
        showWithFlags()
        collapse()
        resetKey = 1
        rule.waitForIdle()
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    @Test
    fun aHeaderStaysCollapsedWhileTheListHasNoRoomToScroll() {
        canScroll = false
        showWithFlags()
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("header").assertIsDisplayed()
    }

    @Test
    fun aCollapsedHeaderComesBackWhenTheListShrinksBelowScrolling() {
        var rows by mutableIntStateOf(40)
        rule.setContent {
            val list = rememberLazyListState()
            CollapsibleHeader(
                collapsible = true,
                scrollRoom = { list.canScrollForward || list.canScrollBackward },
                header = { Box(Modifier.fillMaxWidth().height(120.dp).testTag("header")) { Text("Header") } },
            ) {
                LazyColumn(Modifier.weight(1f).testTag("list"), state = list) {
                    items((1..rows).toList()) { Text("Row $it", Modifier.height(48.dp)) }
                }
            }
        }
        collapse()

        rows = 2
        rule.waitForIdle()

        rule.onNodeWithTag("header").assertIsDisplayed()
    }
}

package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRevealGuardTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var grid: LazyGridState
    private lateinit var scope: CoroutineScope

    private fun show() =
        rule.setContent {
            grid = rememberLazyGridState()
            scope = rememberCoroutineScope()
            LazyVerticalGrid(GridCells.Fixed(1), Modifier.height(300.dp), state = grid) {
                items(20, key = { "row$it" }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                item(key = OFFLINE_HEADER_KEY) { Box(Modifier.fillMaxWidth().height(48.dp)) }
                items(5, key = { "offline$it" }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
            }
        }

    @Test
    fun aScrollTheUserStartedIsNeitherJoinedNorCancelled() {
        show()
        rule.runOnIdle { scope.launch { grid.scrollBy(1_900f) } }
        rule.waitForIdle()
        val before = grid.firstVisibleItemScrollOffset to grid.firstVisibleItemIndex
        var user: Job? = null
        rule.runOnIdle { user = scope.launch { grid.scroll(MutatePriority.UserInput) { awaitCancellation() } } }
        rule.waitForIdle()
        assertTrue(grid.isScrollInProgress)
        rule.runOnIdle { scope.launch { revealOfflineRows(grid, "offline0", 5) } }
        rule.waitForIdle()
        assertTrue("the user's scroll was cancelled", user!!.isActive)
        assertEquals(before, grid.firstVisibleItemScrollOffset to grid.firstVisibleItemIndex)
        user!!.cancel()
    }

    @Test
    fun withTheFirstRowNotComposedItScrollsOnlyAsFarAsNeeded() {
        show()
        // The header sits exactly on the bottom edge, so the first offline row is out of the list's reach.
        rule.runOnIdle { scope.launch { grid.scrollBy(20 * 100f + 48f - 300f) } }
        rule.waitForIdle()
        val start =
            grid.layoutInfo.visibleItemsInfo
                .first { it.key == OFFLINE_HEADER_KEY }
                .offset.y
        rule.runOnIdle { scope.launch { revealOfflineRows(grid, "offline0", 5) } }
        rule.waitForIdle()
        val header = grid.layoutInfo.visibleItemsInfo.first { it.key == OFFLINE_HEADER_KEY }
        val row = grid.layoutInfo.visibleItemsInfo.first { it.key == "offline0" }
        assertEquals("row bottom", 300, row.offset.y + row.size.height)
        assertEquals("moved exactly the row's 100 px", start - 100, header.offset.y)
    }
}

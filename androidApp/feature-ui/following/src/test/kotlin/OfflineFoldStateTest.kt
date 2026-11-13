package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fold's own state, with no offline row composed anywhere: nothing may wait on a row to finish its animation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineFoldStateTest {
    @get:Rule val rule = createComposeRule()

    private var collapsed by mutableStateOf(false)
    private lateinit var fold: OfflineFold

    private fun foldTo(value: Boolean) =
        rule.runOnIdle {
            collapsed = value
            Snapshot.sendApplyNotifications()
        }

    private fun show() =
        rule.setContent {
            fold = rememberOfflineFold(rememberLazyGridState(), collapsed, firstRowKey = "row")
        }

    @Test
    fun theRowsLeaveTheListAfterAFoldEvenIfNoneWasOnScreen() {
        show()
        assertTrue(fold.rowsInList)
        rule.mainClock.autoAdvance = false
        foldTo(true)
        rule.mainClock.advanceTimeByFrame()
        assertTrue("still shrinking", fold.rowsInList)
        rule.mainClock.advanceTimeBy(2_000)
        assertFalse(fold.rowsInList)
    }

    @Test
    fun theRowsJoinTheListAtOnceOnUnfoldAndTheSectionSettles() {
        collapsed = true
        show()
        assertFalse(fold.rowsInList)
        assertFalse(fold.settledExpanded)
        rule.mainClock.autoAdvance = false
        foldTo(false)
        rule.mainClock.advanceTimeByFrame()
        assertTrue(fold.rowsInList)
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue("rows composed late appear at full size", fold.settledExpanded)
    }
}

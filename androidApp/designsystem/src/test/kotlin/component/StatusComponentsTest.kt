package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusComponentsTest {
    @get:Rule val rule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun inlineErrorChipRetryCallsBack() {
        var retries = 0
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                InlineErrorChip(Platform.KICK, LurkError.Offline, onRetry = { retries++ })
            }
        }
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun accountButtonHasContentDescription() {
        var taps = 0
        rule.setContent { LurkTheme(dynamicColor = false) { LurkTopBar("Following", onAccount = { taps++ }) } }
        rule.onNodeWithContentDescription("Account and settings").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun filterChipsSelectOne() {
        var selected by mutableStateOf(PlatformFilter.ALL)
        rule.setContent {
            LurkTheme(dynamicColor = false) { PlatformFilterChips(selected, onSelect = { selected = it }) }
        }
        rule.onNodeWithText("All").assertIsSelected()
        rule.onNodeWithText("Twitch").performClick()
        assertEquals(PlatformFilter.TWITCH, selected)
        rule.onNodeWithText("Twitch").assertIsSelected()
    }

    @Test
    fun inlineErrorChipShowsTheReasonAndNoRetryWhenRetryCannotHelp() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                InlineErrorChip(Platform.TWITCH, LurkError.IntegrityRequired, onRetry = {})
            }
        }
        val reason = text(R.string.error_integrity_required)
        rule.onNodeWithText(reason).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Twitch").assertDoesNotExist()
    }

    @Test
    fun inlineErrorChipShowsTheReasonAndRetryWhenRetryCanHelp() {
        rule.setContent {
            LurkTheme(dynamicColor = false) { InlineErrorChip(Platform.KICK, LurkError.Timeout, onRetry = {}) }
        }
        rule.onNodeWithText(text(R.string.error_timeout)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").assertIsDisplayed()
    }
}

package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformErrorsTest {
    @get:Rule val rule = createComposeRule()

    private val retried = mutableListOf<Platform>()
    private val handlers = ErrorHandlers()

    private fun show(
        failures: List<Pair<Platform, LurkError>>,
        nothingElse: Boolean = false,
    ) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(handlers) {
                    LazyVerticalGrid(GridCells.Fixed(1)) {
                        platformErrors(failures, nothingElse, R.string.error_load_platform) { retried += it }
                    }
                }
            }
        }
    }

    private val both = listOf(Platform.TWITCH to LurkError.Timeout, Platform.KICK to LurkError.Timeout)

    @Test
    fun sameErrorOnBothPlatformsIsOneBanner() {
        show(both)
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(1)
        rule.onNodeWithText("Couldn’t load Twitch or Kick").assertIsDisplayed()
        rule.onNodeWithText("That took too long.").assertIsDisplayed()
    }

    @Test
    fun theMergedRetryRetriesBothPlatforms() {
        show(both)
        rule.onNodeWithText("Retry").performClick()
        assertEquals(listOf(Platform.TWITCH, Platform.KICK), retried)
    }

    @Test
    fun differentErrorsKeepSeparateBanners() {
        show(listOf(Platform.TWITCH to LurkError.Timeout, Platform.KICK to LurkError.Offline))
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(2)
        rule.onNodeWithText("Couldn’t load Twitch").assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
    }

    @Test
    fun signInErrorsAreNeverMerged() {
        show(listOf(Platform.TWITCH to LurkError.NotSignedIn, Platform.KICK to LurkError.NotSignedIn))
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(2)
        rule.onNodeWithText("Couldn’t load Twitch").assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
    }

    @Test
    fun expiredSessionsAreNeverMerged() {
        show(listOf(Platform.TWITCH to LurkError.AuthExpired, Platform.KICK to LurkError.AuthExpired))
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(2)
        rule.onNodeWithText("Couldn’t load Twitch").assertIsDisplayed()
    }

    @Test
    fun onlyIdenticalErrorsMergeSoDifferentHttpCodesStaySeparate() {
        show(listOf(Platform.TWITCH to LurkError.Http(500), Platform.KICK to LurkError.Http(503)))
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(2)
    }

    @Test
    fun rateLimitsWithDifferentWaitsStaySeparate() {
        show(
            listOf(
                Platform.TWITCH to LurkError.RateLimited(5.seconds),
                Platform.KICK to LurkError.RateLimited(9.seconds),
            ),
        )
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(2)
    }

    @Test
    fun aSingleFailureIsAnUnchangedBanner() {
        show(listOf(Platform.KICK to LurkError.Timeout))
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(1)
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
    }

    @Test
    fun allFailedStillShowsTheOneFullScreenState() {
        show(both, nothingElse = true)
        rule.onAllNodesWithTag(ErrorTags.STATE).assertCountEquals(1)
        rule.onAllNodesWithTag(ErrorTags.BANNER).assertCountEquals(0)
    }

    @Test
    fun footerForBothPlatformsIsOneBanner() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(handlers) {
                    ErrorFooter(listOf(Platform.TWITCH, Platform.KICK), LurkError.Offline, onRetry = {
                        retried +=
                            Platform.KICK
                    })
                }
            }
        }
        rule.onNodeWithText("Couldn’t load more from Twitch or Kick").assertIsDisplayed()
    }

    @Test
    fun theListNamesEveryPlatformAndEndsWithOr() {
        var text = ""
        rule.setContent { text = platformList(listOf("Twitch", "Kick", "X")) }
        rule.waitForIdle()
        assertEquals("Twitch, Kick or X", text)
    }
}

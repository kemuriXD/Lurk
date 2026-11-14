package io.github.kemurixd.lurk.designsystem.component

import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.ErrorAction
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.action
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ErrorComponentsTest {
    @get:Rule val rule = createComposeRule()

    private val signIns = mutableListOf<Platform>()
    private var proxySettings = 0
    private var updates = 0
    private var backs = 0
    private var retries = 0

    private val handlers =
        ErrorHandlers(
            signIn = { signIns += it },
            openProxySettings = { proxySettings++ },
            checkForUpdate = { updates++ },
            goBack = { backs++ },
        )

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { LurkTheme(dynamicColor = false) { ErrorHost(handlers) { content() } } }
    }

    @Test
    fun errorStateShowsTitleReasonAndRetry() {
        show { ErrorState("Couldn’t load categories", LurkError.Offline, onRetry = { retries++ }) }
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load categories").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.error_offline)).assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun errorStateGoesBackFromNotFound() {
        show {
            ErrorState(
                "Couldn’t load this category",
                LurkError.NotFound,
                onRetry = { retries++ },
                action = LurkError.NotFound.action(firstPage = true, canGoBack = true),
            )
        }
        rule.onNodeWithText("Go back").performClick()
        assertEquals(1, backs)
        assertEquals(0, retries)
    }

    @Test
    fun bannerShowsTitleReasonAndRetryNamingThePlatform() {
        show { ErrorBanner("Couldn’t load Kick", LurkError.Timeout, onRetry = { retries++ }, platform = Platform.KICK) }
        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.error_timeout)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun authExpiredSignsInAgainToThatPlatform() {
        show { ErrorBanner("Couldn’t load Twitch", LurkError.AuthExpired, onRetry = {}, platform = Platform.TWITCH) }
        rule.onNodeWithText("Sign in again").performClick()
        assertEquals(listOf(Platform.TWITCH), signIns)
    }

    @Test
    fun notSignedInSignsInToThatPlatform() {
        show { ErrorBanner("Couldn’t load Kick", LurkError.NotSignedIn, onRetry = {}, platform = Platform.KICK) }
        rule.onNodeWithText("Sign in").performClick()
        assertEquals(listOf(Platform.KICK), signIns)
    }

    @Test
    fun proxyFailureOpensProxySettings() {
        show {
            ErrorBanner(
                "Couldn’t load Kick",
                LurkError.ProxyFailed,
                onRetry = {},
                platform = Platform.KICK,
                action = LurkError.ProxyFailed.action(),
            )
        }
        rule.onNodeWithText("Open proxy settings").performClick()
        assertEquals(1, proxySettings)
    }

    @Test
    fun schemaChangeChecksForAnUpdate() {
        show { ErrorState("Couldn’t load categories", LurkError.SchemaChanged, onRetry = {}) }
        rule.onNodeWithText("Check for update").performClick()
        assertEquals(1, updates)
    }

    @Test
    fun footerSaysCouldntLoadMoreAndRetriesWhenItCanHelp() {
        show { ErrorFooter(Platform.KICK, LurkError.Timeout, onRetry = { retries++ }) }
        rule.onNodeWithTag(ErrorTags.FOOTER).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load more from Kick").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.error_timeout)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun footerOffersNothingWhenNothingCanHelp() {
        show { ErrorFooter(Platform.TWITCH, LurkError.IntegrityRequired, onRetry = {}) }
        rule.onNodeWithText(text(R.string.error_integrity_required)).assertIsDisplayed()
        rule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun rateLimitWithAKnownWaitCountsDownBeforeRetry() {
        rule.mainClock.autoAdvance = false
        show { ErrorBanner("Couldn’t load Kick", LurkError.RateLimited(2.seconds), onRetry = { retries++ }) }
        rule.onNodeWithText("Retry in 2s").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Retry in 1s").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Retry").assertIsEnabled().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun retryWithAKnownWaitCountsDownAgainAfterEachTry() {
        rule.mainClock.autoAdvance = false
        show { ErrorBanner("Couldn’t load Kick", LurkError.RateLimited(2.seconds), onRetry = { retries++ }) }
        rule.mainClock.advanceTimeBy(2_200)
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Retry in 2s").assertIsNotEnabled()
        assertEquals(1, retries)
    }

    private val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Test
    fun bannerAnnouncesTitleAndReasonOnce() {
        show { ErrorBanner("Couldn’t load Kick", LurkError.Timeout, onRetry = {}, platform = Platform.KICK) }
        rule.onAllNodes(live, useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(live).assert(hasText("Couldn’t load Kick")).assert(hasText(text(R.string.error_timeout)))
    }

    @Test
    fun footerAnnouncesTitleAndReasonOnce() {
        show { ErrorFooter(Platform.KICK, LurkError.Timeout, onRetry = {}) }
        rule.onAllNodes(live, useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(live).assert(hasText("Couldn’t load more from Kick")).assert(hasText(text(R.string.error_timeout)))
    }

    @Test
    fun errorStateAnnouncesTitleAndReasonOnceWithTheTitleAsAHeading() {
        show { ErrorState("Couldn’t load categories", LurkError.Offline, onRetry = {}) }
        rule.onAllNodes(live, useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(live).assert(hasText("Couldn’t load categories")).assert(hasText(text(R.string.error_offline)))
        rule.onNode(isHeading() and hasText("Couldn’t load categories"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun snackbarIsAnnouncedOnce() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.Timeout) } }) { Text("fail") }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText(text(R.string.error_timeout)).assertIsDisplayed()
        rule.onAllNodes(live, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun errorStateRetryDoesNotNameOnePlatform() {
        show { ErrorState("Couldn’t load live channels", LurkError.Offline, onRetry = {}, platform = Platform.KICK) }
        rule.onNodeWithText("Retry").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Kick").assertDoesNotExist()
    }

    @Test
    fun signInIsHiddenWithoutAPlatformToSignInTo() {
        show { ErrorBanner("Couldn’t load", LurkError.NotSignedIn, onRetry = {}) }
        rule.onNodeWithText(text(R.string.error_not_signed_in)).assertIsDisplayed()
        rule.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test
    fun snackbarSignInIsHiddenWithoutAPlatform() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.AuthExpired) } }) { Text("fail") }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText(text(R.string.error_auth_expired)).assertIsDisplayed()
        rule.onNodeWithText("Sign in again").assertDoesNotExist()
    }

    @Test
    fun snackbarNeverOffersGoBack() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.NotFound) } }) { Text("fail") }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText(text(R.string.error_not_found)).assertIsDisplayed()
        rule.onNodeWithText("Go back").assertDoesNotExist()
    }

    @Test
    fun snackbarSaysWhatFailedBeforeWhy() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(
                onClick = {
                    scope.launch {
                        snackbar.showError(
                            LurkError.Timeout,
                            title = "Couldn’t follow Streamer One",
                        )
                    }
                },
            ) { Text("fail") }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText("Couldn’t follow Streamer One. ${text(R.string.error_timeout)}").assertIsDisplayed()
    }

    @Test
    fun explicitActionWinsOverTheMapping() {
        show {
            ErrorBanner(
                "Couldn’t sign in",
                LurkError.AuthExpired,
                onRetry = { retries++ },
                action = ErrorAction.Retry(),
            )
        }
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        assertEquals(emptyList<Platform>(), signIns)
    }

    @Test
    fun snackbarActionRetries() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.Timeout, onRetry = { retries++ }) } }) {
                Text("fail")
            }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText(text(R.string.error_timeout)).assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun snackbarActionSignsInAgain() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.AuthExpired, platform = Platform.KICK) } }) {
                Text("fail")
            }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText("Sign in again").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Platform.KICK), signIns)
    }

    @Test
    fun snackbarWithoutAnActionShowsOnlyTheReason() {
        show {
            val snackbar = rememberErrorSnackbar()
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { snackbar.showError(LurkError.Unexpected, action = null) } }) {
                Text("fail")
            }
        }
        rule.onNodeWithText("fail").performClick()
        rule.onNodeWithText(text(R.string.error_unexpected)).assertIsDisplayed()
        rule.onNodeWithText("Retry").assertDoesNotExist()
    }

    private val busy = mutableStateOf(true)
    private val progress get() = rule.onAllNodesWithTag(ErrorTags.RETRY_PROGRESS)

    @Test
    fun errorStateRetryingDisablesRetryAndShowsProgressOnlyAfterTheDelay() {
        rule.mainClock.autoAdvance = false
        show {
            ErrorState(
                "Couldn’t load categories",
                LurkError.Offline,
                onRetry = { retries++ },
                retrying = busy.value,
            )
        }

        // A fast failure must not dim the button: it looks live and ignores taps for the first 300 ms.
        rule.onNodeWithText("Retry").assertIsEnabled()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(0, retries)
        progress.assertCountEquals(0)
        rule.mainClock.advanceTimeBy(250)
        progress.assertCountEquals(0)
        rule.onNodeWithText("Retry").assertIsEnabled()
        rule.mainClock.advanceTimeBy(150)
        progress.assertCountEquals(1)
        rule.onNodeWithText("Retry").assertIsNotEnabled()
        assertEquals(0, retries)
    }

    @Test
    fun theSpinnerTakesTheLabelsPlaceAndTheButtonKeepsItsWidth() {
        rule.mainClock.autoAdvance = false
        show { ErrorBanner("Couldn’t load Kick", LurkError.Timeout, onRetry = {}, retrying = busy.value) }
        val button = rule.onNode(hasText("Retry") and hasClickAction())
        val before = button.getBoundsInRoot()

        rule.mainClock.advanceTimeBy(400)

        progress.assertCountEquals(1)
        val after = button.getBoundsInRoot()
        assertEquals(before.right - before.left, after.right - after.left)
        assertEquals(before.bottom - before.top, after.bottom - after.top)
        assertEquals(before.left, after.left)
    }

    @Test
    fun aRetryThatEndsBeforeTheDelayNeverShowsProgress() {
        rule.mainClock.autoAdvance = false
        show { ErrorState("Couldn’t load categories", LurkError.Offline, onRetry = {}, retrying = busy.value) }

        repeat(8) {
            progress.assertCountEquals(0)
            rule.mainClock.advanceTimeBy(16)
        }
        busy.value = false
        rule.mainClock.advanceTimeBy(1_000)

        progress.assertCountEquals(0)
        rule.onNodeWithText("Retry").assertIsEnabled()
    }

    @Test
    fun progressGoesWhenTheRetryEnds() {
        rule.mainClock.autoAdvance = false
        show { ErrorState("Couldn’t load categories", LurkError.Offline, onRetry = {}, retrying = busy.value) }
        rule.mainClock.advanceTimeBy(400)
        progress.assertCountEquals(1)

        busy.value = false
        rule.mainClock.advanceTimeBy(32)

        progress.assertCountEquals(0)
        rule.onNodeWithText("Retry").assertIsEnabled()
    }

    @Test
    fun bannerRetryingKeepsTheBannerAndDisablesItsAction() {
        rule.mainClock.autoAdvance = false
        show {
            ErrorBanner(
                "Couldn’t load Kick",
                LurkError.Timeout,
                onRetry = { retries++ },
                platform = Platform.KICK,
                retrying = busy.value,
            )
        }
        rule.mainClock.advanceTimeBy(400)

        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load Kick").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsNotEnabled()
        progress.assertCountEquals(1)
    }

    @Test
    fun footerRetryingKeepsTheFooterAndDisablesItsAction() {
        rule.mainClock.autoAdvance = false
        show { ErrorFooter(Platform.TWITCH, LurkError.Timeout, onRetry = { retries++ }, retrying = busy.value) }
        rule.mainClock.advanceTimeBy(400)

        rule.onNodeWithTag(ErrorTags.FOOTER).assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsNotEnabled()
        progress.assertCountEquals(1)
    }

    @Test
    fun retryingLeavesOtherActionsAlone() {
        show {
            ErrorState(
                "Couldn’t load this category",
                LurkError.NotFound,
                onRetry = {},
                action = LurkError.NotFound.action(firstPage = true, canGoBack = true),
                retrying = true,
            )
        }
        rule.onNodeWithText("Go back").assertIsEnabled()
        progress.assertCountEquals(0)
    }
}

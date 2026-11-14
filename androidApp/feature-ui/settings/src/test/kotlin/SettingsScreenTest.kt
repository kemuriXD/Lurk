package io.github.kemurixd.lurk.featureui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.component.ErrorTags
import io.github.kemurixd.lurk.designsystem.component.PlatformTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.AccountRow
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import io.github.kemurixd.lurk.feature.accounts.AccountsEffect
import io.github.kemurixd.lurk.feature.accounts.AccountsIntent
import io.github.kemurixd.lurk.feature.accounts.AccountsUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val effects = Channel<AccountsEffect>(Channel.BUFFERED)
    private val intents = mutableListOf<AccountsIntent>()
    private val logins = mutableListOf<Platform>()
    private val bannerSignIns = mutableListOf<Platform>()

    /** Answers intents the way the view model does, so the screen's effect handling runs end to end. */
    private fun onIntent(intent: AccountsIntent) {
        intents += intent
        when (intent) {
            is AccountsIntent.SignIn -> effects.trySend(AccountsEffect.OpenLogin(intent.platform))
            is AccountsIntent.SignOut -> effects.trySend(AccountsEffect.AskConfirmSignOut(intent.platform))
            is AccountsIntent.ConfirmSignOut -> Unit
        }
    }

    private fun show(
        twitch: AccountStatus,
        kick: AccountStatus,
    ) {
        val state = AccountsUiState(listOf(AccountRow(Platform.TWITCH, twitch), AccountRow(Platform.KICK, kick)))
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers(signIn = { bannerSignIns += it })) {
                    SettingsScreen(
                        state = state,
                        effects = effects.receiveAsFlow(),
                        onIntent = ::onIntent,
                        onOpenLogin = { logins += it },
                    )
                }
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = rule.activity.getString(id, *args)

    private fun action(platform: Platform) = rule.onNodeWithTag(SettingsTestTags.action(platform))

    private fun logoInAction(platform: Platform) =
        rule.onAllNodes(
            hasTestTag(PlatformTags.logo(platform)) and hasAnyAncestor(hasTestTag(SettingsTestTags.action(platform))),
            useUnmergedTree = true,
        )

    @Test
    fun signInButtonsLeadWithThePlatformLogo() {
        show(twitch = AccountStatus.SignedOut, kick = AccountStatus.Expired("Streamer One"))

        logoInAction(Platform.TWITCH).assertCountEquals(1)
        logoInAction(Platform.KICK).assertCountEquals(1)
    }

    @Test
    fun signOutButtonHasNoLogo() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedIn("Streamer Two", null))

        logoInAction(Platform.TWITCH).assertCountEquals(0)
    }

    @Test
    fun signInButtonOpensLogin() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedOut)

        action(Platform.KICK).assertIsDisplayed()
        rule
            .onNode(
                hasTestTag(SettingsTestTags.action(Platform.KICK)) and hasText(string(R.string.sign_in)),
            ).performClick()
        rule.waitForIdle()

        assertEquals(listOf<AccountsIntent>(AccountsIntent.SignIn(Platform.KICK)), intents)
        assertEquals(listOf(Platform.KICK), logins)
    }

    @Test
    fun signedInRowShowsTheNameAndSignOut() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedOut)

        rule.onNodeWithText("Streamer One", substring = true).assertIsDisplayed()
        rule
            .onNode(hasTestTag(SettingsTestTags.action(Platform.TWITCH)) and hasText(string(R.string.sign_out)))
            .assertIsDisplayed()
    }

    @Test
    fun expiredSessionShowsABannerThatSignsInAgain() {
        show(twitch = AccountStatus.Expired("Streamer One"), kick = AccountStatus.SignedOut)

        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithText("Twitch session expired").assertIsDisplayed()
        rule
            .onNode(hasAnyAncestor(hasTestTag(ErrorTags.BANNER)) and hasText("Sign in again") and hasClickAction())
            .performClick()

        assertEquals(listOf(Platform.TWITCH), bannerSignIns)
    }

    @Test
    fun noBannerWhileNoSessionHasExpired() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedOut)

        rule.onNodeWithTag(ErrorTags.BANNER).assertDoesNotExist()
    }

    @Test
    fun expiredRowOffersSignInAgain() {
        show(twitch = AccountStatus.Expired("Streamer One"), kick = AccountStatus.SignedOut)

        rule.onNodeWithText(string(R.string.status_expired)).assertIsDisplayed()
        rule
            .onNode(hasTestTag(SettingsTestTags.action(Platform.TWITCH)) and hasText(string(R.string.sign_in_again)))
            .performClick()
        rule.waitForIdle()

        assertEquals(listOf<AccountsIntent>(AccountsIntent.SignIn(Platform.TWITCH)), intents)
        assertEquals(listOf(Platform.TWITCH), logins)
    }

    @Test
    fun expiredRowCanSignOutWithTheSameConfirm() {
        show(twitch = AccountStatus.Expired("Streamer One"), kick = AccountStatus.SignedOut)

        rule
            .onNode(hasTestTag(SettingsTestTags.action(Platform.TWITCH)) and hasText(string(R.string.sign_in_again)))
            .assertIsDisplayed()
        rule.onNodeWithTag(SettingsTestTags.signOut(Platform.TWITCH)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.CONFIRM_SIGN_OUT).performClick()
        rule.waitForIdle()

        assertEquals(
            listOf(AccountsIntent.SignOut(Platform.TWITCH), AccountsIntent.ConfirmSignOut(Platform.TWITCH)),
            intents,
        )
    }

    @Test
    fun signOutAsksConfirmFirst() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedIn("Streamer One", null))

        action(Platform.KICK).performClick()
        rule.waitForIdle()

        assertEquals(listOf<AccountsIntent>(AccountsIntent.SignOut(Platform.KICK)), intents)
        rule.onNode(isDialog()).assertIsDisplayed()
        rule.onNodeWithTag(SettingsTestTags.CONFIRM_SIGN_OUT).performClick()
        rule.waitForIdle()

        assertEquals(
            listOf(AccountsIntent.SignOut(Platform.KICK), AccountsIntent.ConfirmSignOut(Platform.KICK)),
            intents,
        )
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun dismissingTheDialogKeepsTheAccount() {
        show(twitch = AccountStatus.SignedIn("Streamer One", null), kick = AccountStatus.SignedOut)

        action(Platform.TWITCH).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(string(R.string.cancel)) and hasClickAction()).performClick()
        rule.waitForIdle()

        assertEquals(listOf<AccountsIntent>(AccountsIntent.SignOut(Platform.TWITCH)), intents)
        rule.onNode(isDialog()).assertDoesNotExist()
        assertTrue(logins.isEmpty())
    }
}

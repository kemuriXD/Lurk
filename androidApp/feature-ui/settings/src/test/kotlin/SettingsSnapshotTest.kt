package io.github.kemurixd.lurk.featureui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.accounts.AccountRow
import io.github.kemurixd.lurk.feature.accounts.AccountStatus
import io.github.kemurixd.lurk.feature.accounts.AccountsUiState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Baselines for the Accounts section, named after the Figma frames (Settings / Accounts / State / Theme). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class SettingsSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        name: String,
        twitch: AccountStatus,
        kick: AccountStatus,
        largeFont: Boolean = false,
    ) = rule.snapshots(name, largeFont) {
        SettingsScreen(
            state = AccountsUiState(listOf(AccountRow(Platform.TWITCH, twitch), AccountRow(Platform.KICK, kick))),
            effects = emptyFlow(),
            onIntent = {},
            onOpenLogin = {},
        )
    }

    private val signedInTwitch = AccountStatus.SignedIn("Streamer One", avatarUrl = null)
    private val signedInKick = AccountStatus.SignedIn("Streamer Two", avatarUrl = null)

    @Test
    fun bothSignedIn() = show("Settings_Accounts_BothSignedIn", signedInTwitch, signedInKick, largeFont = true)

    @Test
    fun kickSignedOut() = show("Settings_Accounts_KickSignedOut", signedInTwitch, AccountStatus.SignedOut)

    @Test
    fun bothSignedOut() = show("Settings_Accounts_BothSignedOut", AccountStatus.SignedOut, AccountStatus.SignedOut)

    @Test
    fun twitchSignInAgain() =
        show("Settings_Accounts_TwitchSignInAgain", AccountStatus.Expired("Streamer One"), signedInKick)
}

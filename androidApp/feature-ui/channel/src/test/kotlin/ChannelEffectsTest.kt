package io.github.kemurixd.lurk.featureui.channel

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.channel.ChannelEffect
import io.github.kemurixd.lurk.feature.channel.ChannelIntent
import io.github.kemurixd.lurk.feature.channel.ChannelUiState
import io.github.kemurixd.lurk.feature.channel.FollowState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import io.github.kemurixd.lurk.core.model.Channel as ChannelProfile
import io.github.kemurixd.lurk.designsystem.R as DesignR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelEffectsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val effects = Channel<ChannelEffect>(Channel.BUFFERED)
    private val players = mutableListOf<Stream>()
    private val errors = mutableListOf<LurkError>()
    private var couldNotOpen = 0
    private val intents = mutableListOf<ChannelIntent>()
    private val signIns = mutableListOf<Platform>()

    private var channelState =
        ChannelUiState(
            PlatformSlice.Loaded(
                ChannelProfile(
                    ref = ChannelRef(Platform.KICK, "streamer1"),
                    id = "1000001",
                    chatId = "streamer1",
                    displayName = "Streamer One",
                    avatarUrl = null,
                    bannerUrl = null,
                    bio = null,
                    followers = null,
                    live = null,
                    lastStream = null,
                ),
            ),
            FollowState.NotFollowing,
        )

    /** The entry's own wiring: effects to snackbars, in the app's one error host. */
    private fun showWithSnackbar() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers(signIn = { signIns += it })) {
                    ChannelEffectsWithSnackbar(
                        ref = ChannelRef(Platform.KICK, "streamer1"),
                        state = { channelState },
                        effects = effects.receiveAsFlow(),
                        onOpenPlayer = { players += it },
                        onIntent = { intents += it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int): String = rule.activity.getString(id)

    @Test
    fun failedFollowShowsASnackbarWhoseRetryFollowsAgain() {
        showWithSnackbar()
        effects.trySend(ChannelEffect.ShowError(LurkError.Timeout))
        rule.onNodeWithText("Couldn’t follow Streamer One. ${text(DesignR.string.error_timeout)}").assertIsDisplayed()

        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()

        assertEquals(listOf<ChannelIntent>(ChannelIntent.ToggleFollow), intents)
    }

    @Test
    fun failedUnfollowSaysUnfollow() {
        channelState = channelState.copy(follow = FollowState.Following)
        showWithSnackbar()
        effects.trySend(ChannelEffect.ShowError(LurkError.Timeout))
        rule.onNodeWithText("Couldn’t unfollow Streamer One. ${text(DesignR.string.error_timeout)}").assertIsDisplayed()
    }

    @Test
    fun expiredSessionOnFollowSignsInAgainToThisPlatform() {
        showWithSnackbar()
        effects.trySend(ChannelEffect.ShowError(LurkError.AuthExpired))

        rule.onNodeWithText("Sign in again").performClick()
        rule.waitForIdle()

        assertEquals(listOf(Platform.KICK), signIns)
        assertEquals(emptyList<ChannelIntent>(), intents)
    }

    private fun handle() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                HandleChannelEffects(
                    effects = effects.receiveAsFlow(),
                    onOpenPlayer = { players += it },
                    onError = { errors += it },
                    onCouldNotOpen = { couldNotOpen++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun nextStarted(): Intent? = shadowOf(rule.activity).nextStartedActivity

    @Test
    fun shareTextOpensTheSystemShareSheet() {
        handle()
        effects.trySend(ChannelEffect.ShareText("https://kick.com/streamer1"))
        rule.waitForIdle()

        val chooser = nextStarted()
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("https://kick.com/streamer1", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun openExternalOpensACustomTab() {
        handle()
        effects.trySend(ChannelEffect.OpenExternal("https://www.twitch.tv/streamer1"))
        rule.waitForIdle()

        val view = nextStarted()
        assertEquals(Intent.ACTION_VIEW, view!!.action)
        assertEquals("https://www.twitch.tv/streamer1", view.dataString)
    }

    @Test
    fun openPlayerAndErrorsGoToTheCallbacks() {
        handle()
        val stream =
            Stream(ChannelRef(Platform.TWITCH, "streamer1"), "1000001", "Streamer One", "t", null, 1, null, null, null)
        effects.trySend(ChannelEffect.OpenPlayer(stream))
        effects.trySend(ChannelEffect.ShowError(LurkError.Blocked))
        rule.waitForIdle()

        assertEquals(listOf(stream), players)
        assertEquals(listOf<LurkError>(LurkError.Blocked), errors)
        assertNull(nextStarted())
    }
}

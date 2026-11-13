package io.github.kemurixd.lurk.featureui.channel

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.channel.ChannelEffect
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelEffectsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val effects = Channel<ChannelEffect>(Channel.BUFFERED)
    private val players = mutableListOf<Stream>()
    private val errors = mutableListOf<LurkError>()

    private fun handle() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                HandleChannelEffects(
                    effects = effects.receiveAsFlow(),
                    onOpenPlayer = { players += it },
                    onError = { errors += it },
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

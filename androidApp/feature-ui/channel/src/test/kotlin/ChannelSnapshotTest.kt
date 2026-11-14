package io.github.kemurixd.lurk.featureui.channel

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.rememberErrorSnackbar
import io.github.kemurixd.lurk.feature.channel.ChannelUiState
import io.github.kemurixd.lurk.feature.channel.FollowState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Baselines for the Channel page, named after the Figma frames (Channel / Channel / State / Theme). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class ChannelSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val category = Category(Platform.TWITCH, "42", "Retro Racers", null)

    private fun show(
        name: String,
        state: ChannelUiState,
        largeFont: Boolean = false,
        withSnackbar: Boolean = false,
    ) = rule.snapshots(name, largeFont) {
        if (withSnackbar) {
            val snackbar = rememberErrorSnackbar()
            LaunchedEffect(Unit) {
                snackbar.showError(LurkError.Timeout, title = "Couldn’t follow Streamer One")
            }
        }
        ChannelScreen(
            ref = ChannelRef(Platform.TWITCH, "streamer1"),
            state = state,
            onIntent = {},
            onSignIn = {},
            onBack = {},
            now = now,
        )
    }

    @Test
    fun live() = show("Channel_Live", loaded(channel(live = stream())), largeFont = true)

    @Test
    fun liveFollowing() = show("Channel_Live_Following", loaded(channel(live = stream()), FollowState.Following))

    @Test
    fun offline() =
        show(
            "Channel_Offline",
            loaded(channel(lastStream = LastStream("Any% attempts", category, now - 2.days))),
        )

    @Test
    fun signedOut() = show("Channel_SignedOut", loaded(channel(live = stream()), FollowState.Hidden))

    @Test
    fun followFailedSnackbar() =
        show("Channel_FollowFailed_Snackbar", loaded(channel(live = stream())), withSnackbar = true)

    @Test
    fun loading() = show("Channel_Loading", ChannelUiState(PlatformSlice.Loading))

    @Test
    fun failed() = show("Channel_Failed", ChannelUiState(PlatformSlice.Failed(LurkError.Offline)))

    @Test
    fun retryInProgress() =
        show(
            "Channel_Failed_RetryInProgress",
            ChannelUiState(PlatformSlice.Failed(LurkError.Offline), retrying = true),
        )

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.TWITCH, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Speedrun attempts all night",
            category = category,
            viewers = 12_400,
            startedAt = now - 2.days,
            thumbnailUrl = null,
            avatarUrl = null,
        )

    private fun channel(
        live: Stream? = null,
        lastStream: LastStream? = null,
    ) = Channel(
        ref = ChannelRef(Platform.TWITCH, "streamer1"),
        id = "1000001",
        chatId = "streamer1",
        displayName = "Streamer One",
        avatarUrl = null,
        bannerUrl = null,
        bio = "Retro racing and too many spreadsheets.",
        followers = 48_200,
        live = live,
        lastStream = lastStream,
    )

    private fun loaded(
        channel: Channel,
        follow: FollowState = FollowState.NotFollowing,
    ) = ChannelUiState(PlatformSlice.Loaded(channel), follow)
}

package io.github.kemurixd.lurk.designsystem.snapshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.SNAPSHOT_DEVICE
import io.github.kemurixd.lurk.designsystem.component.CategoryTile
import io.github.kemurixd.lurk.designsystem.component.ChannelRow
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.ErrorBanner
import io.github.kemurixd.lurk.designsystem.component.ErrorFooter
import io.github.kemurixd.lurk.designsystem.component.ErrorState
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCard
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.LogoActionButton
import io.github.kemurixd.lurk.designsystem.component.PlatformFilterChips
import io.github.kemurixd.lurk.designsystem.component.StreamCard
import io.github.kemurixd.lurk.designsystem.component.StreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.platformNames
import io.github.kemurixd.lurk.designsystem.component.rememberErrorSnackbar
import io.github.kemurixd.lurk.designsystem.snapshots
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** Baselines for the shared components, named after the Figma System States and component frames. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class ComponentSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun stream(
        platform: Platform = Platform.TWITCH,
        title: String = "Speedrunning all night",
    ) = Stream(
        channel = ChannelRef(platform, "streamer1"),
        channelId = "1000001",
        displayName = "Streamer One",
        title = title,
        category = Category(platform, "1", "Retro Racers", null),
        viewers = 12_400,
        startedAt = Instant.parse("2026-11-13T09:30:00Z"),
        thumbnailUrl = null,
        avatarUrl = null,
        tags = listOf("English", "Speedrun"),
    )

    private fun channel(live: Stream? = null) =
        Channel(
            ref = ChannelRef(Platform.TWITCH, "streamer1"),
            id = "1000001",
            chatId = "streamer1",
            displayName = "Streamer One",
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = live,
            lastStream = LastStream(null, null, Instant.parse("2026-11-10T12:00:00Z")),
        )

    @Test
    fun errorStateOffline() =
        rule.snapshots("System_ErrorState_Offline", largeFont = true) {
            ErrorState("Couldn’t load live channels", LurkError.Offline, onRetry = {})
        }

    @Test
    fun errorStateRetryInProgress() =
        rule.snapshots("System_ErrorState_RetryInProgress") {
            ErrorState("Couldn’t load live channels", LurkError.Offline, onRetry = {}, retrying = true)
        }

    @Test
    fun errorBannerSingle() =
        rule.snapshots("System_ErrorBanner_Single", largeFont = true) {
            ErrorBanner(
                "Couldn’t load Kick",
                LurkError.Timeout,
                onRetry = {},
                platform = Platform.KICK,
                modifier = Modifier.padding(16.dp),
            )
        }

    @Test
    fun errorBannerSignIn() =
        rule.snapshots("System_ErrorBanner_SignInAgain") {
            ErrorBanner(
                "Couldn’t load Twitch",
                LurkError.AuthExpired,
                onRetry = {},
                platform = Platform.TWITCH,
                modifier = Modifier.padding(16.dp),
            )
        }

    @Test
    fun errorBannerMerged() =
        rule.snapshots("System_ErrorBanner_Merged") {
            ErrorBanner(
                stringResource(R.string.error_load_platform, platformNames(listOf(Platform.TWITCH, Platform.KICK))),
                LurkError.Offline,
                onRetry = {},
                modifier = Modifier.padding(16.dp),
            )
        }

    @Test
    fun errorBannerRetryInProgress() =
        rule.snapshots("System_ErrorBanner_RetryInProgress") {
            ErrorBanner(
                "Couldn’t load Kick",
                LurkError.Offline,
                onRetry = {},
                platform = Platform.KICK,
                modifier = Modifier.padding(16.dp),
                retrying = true,
            )
        }

    @Test
    fun errorFooterSingle() =
        rule.snapshots("System_ErrorFooter_Single") {
            ErrorFooter(Platform.KICK, LurkError.Timeout, onRetry = {}, modifier = Modifier.padding(16.dp))
        }

    @Test
    fun errorFooterMerged() =
        rule.snapshots("System_ErrorFooter_Merged") {
            ErrorFooter(
                listOf(Platform.TWITCH, Platform.KICK),
                LurkError.Offline,
                onRetry = {},
                modifier = Modifier.padding(16.dp),
            )
        }

    @Test
    fun errorSnackbar() =
        rule.snapshots("System_ErrorSnackbar") {
            val snackbar = rememberErrorSnackbar()
            LaunchedEffect(Unit) {
                snackbar.showError(LurkError.Timeout, platform = Platform.KICK, title = "Couldn’t follow Streamer One")
            }
        }

    @Test
    fun logoButtons() =
        rule.snapshots("Component_LogoButtons") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoActionButton("Sign in to Twitch", Platform.TWITCH, filled = true, enabled = true, onClick = {})
                LogoActionButton("Sign in to Kick", Platform.KICK, filled = true, enabled = true, onClick = {})
                LogoActionButton("Sign in to Twitch", Platform.TWITCH, filled = false, enabled = true, onClick = {})
                LogoActionButton(
                    "Sign in to Kick",
                    Platform.KICK,
                    filled = true,
                    enabled = false,
                    onClick = {},
                    progress = true,
                )
            }
        }

    @Test
    fun filterChips() =
        rule.snapshots("Component_FilterChips") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                PlatformFilterChips(PlatformFilter.ALL, onSelect = {})
                PlatformFilterChips(PlatformFilter.TWITCH, onSelect = {})
                PlatformFilterChips(PlatformFilter.KICK, onSelect = {})
            }
        }

    @Test
    fun expandedStreamCard() =
        rule.snapshots("Component_ExpandedStreamCard_Live", largeFont = true) {
            ExpandedStreamCard(stream(), onOpenPlayer = {}, onOpenChannel = {})
        }

    @Test
    fun expandedStreamCardStress() =
        rule.snapshots("Component_ExpandedStreamCard_StressLongText") {
            ExpandedStreamCard(
                stream(title = "An extremely long stream title that keeps going well past what two lines can show"),
                onOpenPlayer = {},
                onOpenChannel = {},
            )
        }

    @Test
    fun expandedStreamCardLoading() =
        rule.snapshots("Component_ExpandedStreamCard_Loading") { ExpandedStreamCardPlaceholder() }

    @Test
    fun streamCard() =
        rule.snapshots("Component_StreamCard_Live") {
            Column(Modifier.padding(8.dp)) {
                StreamCard(stream(), onOpenPlayer = {}, onOpenChannel = {})
                StreamCard(stream(Platform.KICK), onOpenPlayer = {}, onOpenChannel = {})
                StreamCardPlaceholder()
            }
        }

    /** A 320 dp phone's card (288 dp): the text goes under the picture instead of squeezing the name. */
    @Test
    fun streamCardNarrow() =
        rule.snapshots("Component_StreamCard_Narrow") {
            Column(Modifier.padding(8.dp).width(288.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamCard(stream(), onOpenPlayer = {}, onOpenChannel = {})
                StreamCardPlaceholder()
            }
        }

    @Test
    fun channelRows() =
        rule.snapshots("Component_ChannelRow") {
            Column {
                ChannelRow(channel(live = stream()), onClick = {})
                ChannelRow(channel(), onClick = {})
            }
        }

    @Test
    fun categoryTile() =
        rule.snapshots("Component_CategoryTile") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(Category(Platform.TWITCH, "1", "Retro Racers", null, 12_400), onClick = {})
                CategoryTile(Category(Platform.KICK, "2", "Cozy Builder", null, 800), onClick = {})
            }
        }

    @Test
    fun emptyState() =
        rule.snapshots("Component_EmptyState") {
            EmptyState("Nobody you follow is live", "Browse to find a stream.", "Browse" to {})
        }
}

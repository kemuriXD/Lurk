package io.github.kemurixd.lurk.featureui.channel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.component.ErrorTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.channel.ChannelIntent
import io.github.kemurixd.lurk.feature.channel.ChannelUiState
import io.github.kemurixd.lurk.feature.channel.FollowState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelScreenTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<ChannelIntent>()
    private var backs = 0
    private var signIns = 0
    private val now = Instant.parse("2026-11-12T12:00:00Z")
    private val category = Category(Platform.TWITCH, "42", "Retro Racers", null)

    private var errorBacks = 0
    private val errorSignIns = mutableListOf<Platform>()

    private fun show(state: ChannelUiState) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers(goBack = { errorBacks++ }, signIn = { errorSignIns += it })) {
                    ChannelScreen(
                        ref = ChannelRef(Platform.TWITCH, "streamer1"),
                        state = state,
                        onIntent = { intents += it },
                        onSignIn = { signIns++ },
                        onBack = { backs++ },
                        now = now,
                    )
                }
            }
        }
    }

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.TWITCH, "streamer1"),
            channelId = "1000001",
            displayName = "Streamer One",
            title = "Speedrun attempts all night",
            category = category,
            viewers = 12_400,
            startedAt = null,
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

    @Test
    fun offlineShowsLastStreamCard() {
        val last = LastStream("Any% attempts", category, now - 2.days)
        show(loaded(channel(lastStream = last)))

        rule.onNodeWithTag(ChannelTestTags.LAST_STREAM).assertIsDisplayed()
        rule.onNodeWithText("Any% attempts · Retro Racers").assertIsDisplayed()
        rule.onNodeWithText("Offline · last live 2 days ago").assertIsDisplayed()
        rule.onNodeWithTag(ChannelTestTags.LIVE_PREVIEW).assertDoesNotExist()
    }

    @Test
    fun offlineStatusUsesWeeksNotDays() {
        show(loaded(channel(lastStream = LastStream(null, null, now - 9.days))))
        rule.onNodeWithText("Offline · last live 1 week ago").assertIsDisplayed()
    }

    @Test
    fun offlineStatusOneDayIsYesterday() {
        show(loaded(channel(lastStream = LastStream(null, null, now - 1.days))))
        rule.onNodeWithText("Offline · last live yesterday").assertIsDisplayed()
    }

    @Test
    fun offlineStatusLongAgoIsYears() {
        show(loaded(channel(lastStream = LastStream(null, null, now - 1_000.days))))
        rule.onNodeWithText("Offline · last live 2 years ago").assertIsDisplayed()
    }

    @Test
    fun offlineStatusWithoutATimeIsJustOffline() {
        show(loaded(channel(lastStream = LastStream("Any% attempts", category, null))))
        rule.onNodeWithTag(ChannelTestTags.STATUS).assertTextContains("Offline")
        rule.onNodeWithText("last live", substring = true).assertDoesNotExist()
    }

    @Test
    fun livePreviewShowsStreamDetails() {
        show(loaded(channel(live = stream())))

        rule.onNodeWithTag(ChannelTestTags.LIVE_PREVIEW).assertIsDisplayed()
        rule.onNodeWithText("Speedrun attempts all night").assertIsDisplayed()
        rule.onNodeWithTag(ChannelTestTags.STATUS).assertTextContains("Streaming Retro Racers · 12.4K viewers")
        rule.onNodeWithTag(ChannelTestTags.LAST_STREAM).assertDoesNotExist()
    }

    @Test
    fun livePreviewTapOpensPlayer() {
        show(loaded(channel(live = stream())))
        rule.onNodeWithTag(ChannelTestTags.LIVE_PREVIEW).performClick()
        assertEquals(listOf<ChannelIntent>(ChannelIntent.WatchLive), intents)
    }

    @Test
    fun backCallsOnBack() {
        show(loaded(channel()))
        rule.onNodeWithTag(ChannelTestTags.BACK).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun backIsAvailableWhileLoading() {
        show(ChannelUiState())
        rule.onNodeWithTag(ChannelTestTags.BACK).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun followButtonTogglesFollow() {
        show(loaded(channel(), FollowState.NotFollowing))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertTextContains("Follow")
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).performClick()
        assertEquals(listOf<ChannelIntent>(ChannelIntent.ToggleFollow), intents)
    }

    @Test
    fun followingStateReadsFollowing() {
        show(loaded(channel(), FollowState.Following))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertTextContains("Following")
    }

    @Test
    fun unknownFollowStateStillOffersFollow() {
        show(loaded(channel(), FollowState.Unknown))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertTextContains("Follow")
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).performClick()
        assertEquals(listOf<ChannelIntent>(ChannelIntent.ToggleFollow), intents)
    }

    @Test
    fun checkingFollowIsDisabled() {
        show(loaded(channel(), FollowState.Checking))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertTextContains("Follow")
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertIsNotEnabled()
    }

    @Test
    fun busyFollowIsDisabledAndShowsTheTarget() {
        show(loaded(channel(), FollowState.Busy(true)))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertTextContains("Following")
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertIsNotEnabled()
    }

    @Test
    fun signedOutOffersSignIn() {
        show(loaded(channel(), FollowState.Hidden))
        rule.onNodeWithTag(ChannelTestTags.FOLLOW).assertDoesNotExist()
        rule.onNodeWithTag(ChannelTestTags.SIGN_IN).performClick()
        assertEquals(1, signIns)
        assertEquals(emptyList<ChannelIntent>(), intents)
    }

    @Test
    fun shareSendsShare() {
        show(loaded(channel()))
        rule.onNodeWithTag(ChannelTestTags.SHARE).performClick()
        assertEquals(listOf<ChannelIntent>(ChannelIntent.Share), intents)
    }

    @Test
    fun failedLoadOffersRetry() {
        show(ChannelUiState(PlatformSlice.Failed(LurkError.Offline), FollowState.Hidden))
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load this channel").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(listOf<ChannelIntent>(ChannelIntent.Retry), intents)
    }

    @Test
    fun signedOutLoadOffersSignInInsteadOfSpinning() {
        show(ChannelUiState(PlatformSlice.NotSignedIn, FollowState.Hidden))
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithContentDescription("Loading").assertDoesNotExist()
        rule.onNodeWithText("Sign in").performClick()
        assertEquals(listOf(Platform.TWITCH), errorSignIns)
    }

    @Test
    fun missingChannelOffersGoingBack() {
        show(ChannelUiState(PlatformSlice.Failed(LurkError.NotFound), FollowState.Hidden))
        rule.onNodeWithText("Go back").performClick()
        assertEquals(1, errorBacks)
        assertEquals(emptyList<ChannelIntent>(), intents)
    }

    @Test
    fun aRetryingErrorStateStaysAndItsRetryIsOff() {
        show(
            ChannelUiState(PlatformSlice.Failed(LurkError.Offline), FollowState.Hidden, retrying = true),
        )
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }
}

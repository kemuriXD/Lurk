package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsIntent
import io.github.kemurixd.lurk.feature.browse.CategoryStreamsUiState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryScreenTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<CategoryStreamsIntent>()
    private val players = mutableListOf<Stream>()
    private var backs = 0

    private fun show(state: CategoryStreamsUiState) {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                CategoryScreen(
                    platform = Platform.KICK,
                    name = "Cozy Builder",
                    state = state,
                    onIntent = { intents += it },
                    onOpenPlayer = { players += it },
                    onOpenChannel = {},
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun headerNamesTheCategoryAndListsStreams() {
        show(state(streams = listOf(stream())))
        rule.onNodeWithText("Cozy Builder").assertIsDisplayed()
        rule.onNodeWithText("Streamer One").assertIsDisplayed()
    }

    @Test
    fun streamCardOpensPlayer() {
        val stream = stream()
        show(state(streams = listOf(stream)))
        rule.onNodeWithTag("stream_card").performClick()
        assertEquals(listOf(stream), players)
    }

    @Test
    fun endOfListTriggersLoadMore() {
        show(state(streams = listOf(stream()), canLoadMore = true))
        rule.waitForIdle()
        assertEquals(listOf<CategoryStreamsIntent>(CategoryStreamsIntent.LoadMore), intents.distinct())
    }

    @Test
    fun notFoundOffersGoingBack() {
        show(state(slice = PlatformSlice.Failed(LurkError.NotFound)))
        rule.onNodeWithText("Nothing found here.").assertIsDisplayed()
        rule.onNodeWithText("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun otherFailuresOfferRetry() {
        show(state(slice = PlatformSlice.Failed(LurkError.Offline)))
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf<CategoryStreamsIntent>(CategoryStreamsIntent.Retry), intents)
    }

    private fun state(
        streams: List<Stream> = emptyList(),
        slice: PlatformSlice<Unit> = PlatformSlice.Loaded(Unit),
        canLoadMore: Boolean = false,
    ) = CategoryStreamsUiState(streams = streams, slice = slice, canLoadMore = canLoadMore, loadingMore = false)

    private fun stream() =
        Stream(
            channel = ChannelRef(Platform.KICK, "streamer1"),
            channelId = "",
            displayName = "Streamer One",
            title = "Test stream",
            category = null,
            viewers = 900,
            startedAt = null,
            thumbnailUrl = null,
            avatarUrl = null,
        )
}

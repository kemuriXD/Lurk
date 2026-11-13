package io.github.kemurixd.lurk.featureui.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.time.LocalClock
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.search.SearchIntent
import io.github.kemurixd.lurk.feature.search.SearchPhase
import io.github.kemurixd.lurk.feature.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    private val allIntents = mutableListOf<SearchIntent>()

    /** What the user's taps and typing sent; rows coming on screen send their own intent, checked separately. */
    private val intents get() = allIntents.filterNot { it is SearchIntent.OfflineRowShown }
    private val channels = mutableListOf<ChannelRef>()
    private val categories = mutableListOf<Category>()
    private var keyboardHidden = 0
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = now
        }

    private val keyboard =
        object : SoftwareKeyboardController {
            override fun hide() {
                keyboardHidden++
            }

            override fun show() = Unit
        }

    private fun show(state: SearchUiState) {
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
                LocalClock provides fixedClock,
            ) {
                LurkTheme(dynamicColor = false) {
                    SearchScreen(
                        state = state,
                        onIntent = { allIntents += it },
                        onOpenChannel = { channels += it },
                        onOpenCategory = { categories += it },
                    )
                }
            }
        }
    }

    @Test
    fun emptyStateInvitesASearch() {
        show(SearchUiState())
        rule.onNodeWithText("Search both platforms").assertIsDisplayed()
        rule.onNodeWithText("Search Twitch and Kick").assertIsDisplayed()
    }

    @Test
    fun typingSendsQueryChanged() {
        show(SearchUiState())
        rule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("pix")
        assertEquals(listOf<SearchIntent>(SearchIntent.QueryChanged("pix")), intents)
    }

    @Test
    fun offlineResultSaysWhenItWasLastLive() {
        val otter = channel("pixelotter").copy(lastStream = LastStream(null, null, now - 30.hours))
        show(results(channels = listOf(otter)))
        rule.onNodeWithText("Offline · last live yesterday").assertIsDisplayed()
    }

    @Test
    fun offlineResultsWithoutATimeAskForItWhenShown() {
        val otter = channel("pixelotter").copy(lastStream = LastStream(null, null, now - 30.hours))
        show(results(channels = listOf(otter, channel("pixelbadger", Platform.KICK))))
        rule.waitForIdle()
        assertEquals(
            listOf<SearchIntent>(SearchIntent.OfflineRowShown(ChannelRef(Platform.KICK, "pixelbadger"))),
            allIntents.filterIsInstance<SearchIntent.OfflineRowShown>(),
        )
    }

    @Test
    fun onlyResultsOnScreenAskForTheirTime() {
        val many = (1..100).map { channel("pixel$it", Platform.KICK) }
        show(results(channels = many))
        rule.waitForIdle()
        val asked = allIntents.filterIsInstance<SearchIntent.OfflineRowShown>().map { it.channel.login }
        assertTrue("the first result asks", "pixel1" in asked)
        assertTrue("results far below the screen don't ask, asked: $asked", "pixel100" !in asked)
    }

    @Test
    fun channelResultOpensChannel() {
        show(results(channels = listOf(channel("pixelotter"))))
        rule.onNodeWithText("Pixelotter").performClick()
        assertEquals(listOf(ChannelRef(Platform.TWITCH, "pixelotter")), channels)
    }

    @Test
    fun categoryResultOpensCategory() {
        val retro = Category(Platform.KICK, "42", "Retro Racers", null)
        show(results(categories = listOf(retro)))
        rule.onNodeWithText("Retro Racers").performClick()
        assertEquals(listOf(retro), categories)
    }

    @Test
    fun clearButtonClears() {
        show(results(query = "pix", channels = listOf(channel("pixelotter"))))
        rule.onNodeWithTag(SearchTestTags.FIELD).assertTextEquals("pix")

        rule.onNodeWithContentDescription("Clear search").performClick()

        assertEquals(listOf<SearchIntent>(SearchIntent.Clear), intents)
        rule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        rule.onNodeWithTag(SearchTestTags.FIELD).assertTextEquals("Search Twitch and Kick", includeEditableText = false)
    }

    @Test
    fun imeSearchActionHidesKeyboardOnly() {
        show(results(query = "pix", channels = listOf(channel("pixelotter"))))

        rule.onNodeWithTag(SearchTestTags.FIELD).performImeAction()

        assertEquals(1, keyboardHidden)
        assertTrue("IME action must not send an intent: $intents", intents.isEmpty())
    }

    @Test
    fun partialFailureShowsRetryChip() {
        show(
            results(
                channels = listOf(channel("pixelotter", Platform.KICK)),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Loaded(Unit),
                    ),
            ),
        )
        rule.onNodeWithText("Couldn’t load Twitch").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Twitch").performClick()
        assertEquals(listOf<SearchIntent>(SearchIntent.Retry(Platform.TWITCH)), intents)
    }

    @Test
    fun noResultsNamesTheQuery() {
        show(SearchUiState(query = " zzqxv ", phase = SearchPhase.NO_RESULTS))
        rule.onNodeWithText("No results for “zzqxv”").assertIsDisplayed()
    }

    @Test
    fun filterChipSendsSelectFilter() {
        show(results(channels = listOf(channel("pixelotter"))))
        rule.onNodeWithText("Kick").performClick()
        assertEquals(listOf<SearchIntent>(SearchIntent.SelectFilter(PlatformFilter.KICK)), intents)
    }

    @Test
    fun typingPhaseShowsSearchingProgress() {
        show(SearchUiState(query = "pix", phase = SearchPhase.TYPING))
        rule.onNodeWithTag(SearchTestTags.PROGRESS).assertIsDisplayed()
    }

    private fun results(
        query: String = "pix",
        channels: List<Channel> = emptyList(),
        categories: List<Category> = emptyList(),
        slices: Map<Platform, PlatformSlice<Unit>> =
            mapOf(Platform.TWITCH to PlatformSlice.Loaded(Unit), Platform.KICK to PlatformSlice.Loaded(Unit)),
    ) = SearchUiState(
        query = query,
        channels = channels,
        categories = categories,
        slices = slices,
        phase = SearchPhase.RESULTS,
    )

    private fun channel(
        login: String,
        platform: Platform = Platform.TWITCH,
    ) = Channel(
        ref = ChannelRef(platform, login),
        id = "1000002",
        chatId = login,
        displayName = login.replaceFirstChar { it.uppercase() },
        avatarUrl = null,
        bannerUrl = null,
        bio = null,
        followers = null,
        live = null,
        lastStream = null,
    )
}

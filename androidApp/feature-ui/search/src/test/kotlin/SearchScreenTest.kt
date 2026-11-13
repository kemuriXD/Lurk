package io.github.kemurixd.lurk.featureui.search

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import io.github.kemurixd.lurk.designsystem.time.LocalClock
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.search.SearchIntent
import io.github.kemurixd.lurk.feature.search.SearchPhase
import io.github.kemurixd.lurk.feature.search.SearchType
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import io.github.kemurixd.lurk.designsystem.R as DesignR

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
                    ErrorHost(ErrorHandlers()) {
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
    fun resultsThatScrollAwayLetGoOfTheirLookup() {
        val many = (1..100).map { channel("pixel$it", Platform.KICK) }
        show(results(channels = many))
        rule.onNodeWithTag(SearchTestTags.LIST).performScrollToNode(hasText("Pixel100"))
        rule.waitForIdle()
        val hidden = allIntents.filterIsInstance<SearchIntent.OfflineRowHidden>().map { it.channel.login }
        assertTrue("the first result lets go once scrolled away, hidden: $hidden", "pixel1" in hidden)
        assertTrue("the result on screen keeps its lookup", "pixel100" !in hidden)
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
    fun partialFailureShowsARetryBanner() {
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
        rule.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        rule.onNodeWithText("Couldn’t load Twitch").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retry loading Twitch").performClick()
        assertEquals(listOf<SearchIntent>(SearchIntent.Retry(Platform.TWITCH)), intents)
    }

    @Test
    fun everyPlatformFailingShowsOneErrorStateThatRetriesBoth() {
        show(
            results(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                    ),
            ),
        )
        rule.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
        rule.onNodeWithTag(ErrorTags.BANNER).assertDoesNotExist()
        rule.onNodeWithText("Couldn’t search").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(
            listOf<SearchIntent>(SearchIntent.Retry(Platform.TWITCH), SearchIntent.Retry(Platform.KICK)),
            intents,
        )
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

    @Test
    fun channelsArrivingAfterCategoriesLeaveTheListAtTheTop() {
        val pixel = Category(Platform.TWITCH, "1000003", "Pixel Game", null, 1000)
        var state by mutableStateOf(results(categories = listOf(pixel)))
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
                LocalClock provides fixedClock,
            ) {
                LurkTheme(dynamicColor = false) {
                    ErrorHost(ErrorHandlers()) {
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
        rule.onNodeWithText("Pixel Game").assertIsDisplayed()

        state = results(channels = (1..30).map { channel("pixel$it") }, categories = listOf(pixel))
        rule.waitForIdle()

        rule.onNodeWithText("Pixel1").assertIsDisplayed()
    }

    @Test
    fun resultsLandingAboveDoNotMoveAListTheUserScrolledToCategories() {
        val pixel = Category(Platform.TWITCH, "1000003", "Pixel Game", null, 1000)
        var state by mutableStateOf(results(channels = (1..30).map { channel("pixel$it") }, categories = listOf(pixel)))
        showLive { state }
        rule.onNodeWithTag(SearchTestTags.LIST).performScrollToNode(hasText("Pixel Game"))
        rule.waitForIdle()
        rule.onNodeWithText("Pixel Game").assertIsDisplayed()

        state = state.copy(channels = listOf(channel("pixel0")) + state.channels)
        rule.waitForIdle()

        rule.onNodeWithText("Pixel Game").assertIsDisplayed()
        rule.onNodeWithText("Pixel0").assertDoesNotExist()
    }

    @Test
    fun aNewQueryScrollsBackToTheTop() {
        val many = (1..30).map { channel("pixel$it") }
        var state by mutableStateOf(results(query = "pix", channels = many))
        showLive { state }
        rule.onNodeWithTag(SearchTestTags.LIST).performScrollToNode(hasText("Pixel30"))
        rule.waitForIdle()
        rule.onNodeWithText("Pixel1").assertDoesNotExist()

        state = results(query = "pixe", channels = many)
        rule.waitForIdle()

        rule.onNodeWithText("Pixel1").assertIsDisplayed()
    }

    @Test
    fun categoriesOnlyFillTheGridInsteadOfOneRow() {
        val games = (1..9).map { Category(Platform.TWITCH, "10$it", "Game$it", null, 1000L * it) }
        show(results(categories = games).copy(type = SearchType.CATEGORIES))
        rule.onNodeWithTag(SearchTestTags.CATEGORY_ROW).assertDoesNotExist()
        val first = rule.onNodeWithText("Game1").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithText("Game2").fetchSemanticsNode().boundsInRoot
        assertEquals("tiles sit side by side", first.top, second.top, 1f)
        assertTrue("the second tile is beside the first", second.left >= first.right - 1f)
        // A third tile fits beside them: the grid fills the width instead of a single scrolling row.
        val third = rule.onNodeWithText("Game3").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, third.top, 1f)
    }

    @Test
    fun categoriesInTheMixedViewStayInOneHorizontalRow() {
        val games = (1..3).map { Category(Platform.TWITCH, "10$it", "Game$it", null, 1000L * it) }
        show(results(channels = listOf(channel("pixelotter")), categories = games))
        rule.onNodeWithTag(SearchTestTags.CATEGORY_ROW).assertExists()
    }

    @Test
    fun noResultsCopyNamesTheTypeBeingFiltered() {
        show(SearchUiState(query = "pix", type = SearchType.CATEGORIES, phase = SearchPhase.NO_RESULTS))
        rule.onNodeWithText("No categories for “pix”").assertIsDisplayed()
    }

    @Test
    fun noResultsCopyNamesChannelsWhenFilteringThem() {
        show(SearchUiState(query = "pix", type = SearchType.CHANNELS, phase = SearchPhase.NO_RESULTS))
        rule.onNodeWithText("No channels for “pix”").assertIsDisplayed()
    }

    private fun showLive(state: () -> SearchUiState) {
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
                LocalClock provides fixedClock,
            ) {
                LurkTheme(dynamicColor = false) {
                    SearchScreen(
                        state = state(),
                        onIntent = { allIntents += it },
                        onOpenChannel = { channels += it },
                        onOpenCategory = { categories += it },
                    )
                }
            }
        }
    }

    @Test
    fun rowsEnteringWhileScrollingDoNotEachStartTheirOwnClock() {
        var clockReads = 0
        val countingClock =
            object : Clock {
                override fun now(): Instant {
                    clockReads++
                    return now
                }
            }
        val many = (1..200).map { channel("pixel$it") }
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
                LocalClock provides countingClock,
            ) {
                LurkTheme(dynamicColor = false) {
                    SearchScreen(
                        state = results(channels = many),
                        onIntent = { allIntents += it },
                        onOpenChannel = { channels += it },
                        onOpenCategory = { categories += it },
                    )
                }
            }
        }
        rule.waitForIdle()
        clockReads = 0
        repeat(8) {
            rule.onNodeWithTag(SearchTestTags.LIST).performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        val entered = allIntents.count { it is SearchIntent.OfflineRowShown }
        assertTrue("only $entered rows entered, too few to mean anything", entered > 30)
        // One ticking clock serves the whole list; a clock per row costs a state and a timer for every row that enters.
        assertTrue("rows read the clock $clockReads times while $entered entered", clockReads <= 2)
    }

    @Test
    fun aClockTickRecomposesNoLiveRow() {
        var current = now
        var clockReads = 0
        val ticking =
            object : Clock {
                override fun now(): Instant {
                    clockReads++
                    return current
                }
            }
        var liveRowReads = 0
        val live = (1..3).map { liveChannel("pixel$it") }
        rule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides keyboard,
                LocalClock provides ticking,
            ) {
                CountingStrings(onRead = { id -> if (id == DesignR.string.viewers_count) liveRowReads++ }) {
                    LurkTheme(dynamicColor = false) {
                        SearchScreen(
                            state = results(channels = live + channel("sleeper1")),
                            onIntent = { allIntents += it },
                            onOpenChannel = { channels += it },
                            onOpenCategory = { categories += it },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        liveRowReads = 0
        clockReads = 0
        current = now + 1.minutes
        rule.mainClock.advanceTimeBy(1.minutes.inWholeMilliseconds + 100)
        rule.waitForIdle()
        assertTrue("the clock never ticked", clockReads > 0)
        // A live row asks for its viewer count each time it recomposes; nothing in it depends on the clock.
        assertEquals("live rows recomposed on the tick", 0, liveRowReads)
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

    private fun liveChannel(login: String) =
        channel(login).copy(
            live =
                Stream(
                    channel = ChannelRef(Platform.TWITCH, login),
                    channelId = "1000001",
                    displayName = login.replaceFirstChar { it.uppercase() },
                    title = "Speedrunning all night",
                    category = null,
                    viewers = 1_200,
                    startedAt = null,
                    thumbnailUrl = null,
                    avatarUrl = null,
                ),
        )
}

/** Counts which strings the content asks for. A composable asks for its strings again each time it recomposes. */
@Composable
private fun CountingStrings(
    onRead: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val base = LocalResources.current
    val counting = remember(base) { CountingResources(base, onRead) }
    CompositionLocalProvider(LocalResources provides counting, content = content)
}

@Suppress("DEPRECATION")
private class CountingResources(
    base: Resources,
    private val onRead: (Int) -> Unit,
) : Resources(base.assets, base.displayMetrics, base.configuration) {
    override fun getString(id: Int): String {
        onRead(id)
        return super.getString(id)
    }

    override fun getString(
        id: Int,
        vararg formatArgs: Any?,
    ): String {
        onRead(id)
        return super.getString(id, *formatArgs)
    }
}

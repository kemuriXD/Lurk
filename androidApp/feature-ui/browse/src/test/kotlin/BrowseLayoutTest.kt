package io.github.kemurixd.lurk.featureui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.browse.CategoriesUiState
import io.github.kemurixd.lurk.feature.browse.TopLiveIntent
import io.github.kemurixd.lurk.feature.browse.TopLiveUiState
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Browse in a landscape phone gives the list the height: the switch and chips scroll away with it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp")
class BrowseLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val intents = mutableListOf<TopLiveIntent>()

    private fun show() {
        val streams =
            (1..30).map {
                Stream(
                    channel = ChannelRef(Platform.TWITCH, "streamer$it"),
                    channelId = "1000001",
                    displayName = "Streamer$it",
                    title = "Test stream",
                    category = null,
                    viewers = 900,
                    startedAt = null,
                    thumbnailUrl = null,
                    avatarUrl = null,
                )
            }
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    BrowseScreen(
                        topLive =
                            TopLiveUiState(
                                filter = PlatformFilter.ALL,
                                streams = streams,
                                slices =
                                    mapOf(
                                        Platform.TWITCH to PlatformSlice.Loaded(Unit),
                                        Platform.KICK to PlatformSlice.Loaded(Unit),
                                    ),
                                canLoadMore = false,
                                loadingMore = false,
                            ),
                        onTopLiveIntent = { intents += it },
                        categories =
                            CategoriesUiState(
                                platform = Platform.TWITCH,
                                categories = emptyList<Category>(),
                                slice = PlatformSlice.Loaded(Unit),
                                canLoadMore = false,
                            ),
                        onCategoriesIntent = {},
                        onOpenPlayer = {},
                        onOpenChannel = {},
                        onOpenCategory = {},
                    )
                }
            }
        }
    }

    @Test
    fun theTabsAndChipsScrollAwayWithTheListInALandscapePhone() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TAB_TOP_LIVE).assertIsDisplayed()
        rule.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).performTouchInput { swipeUp() }
        rule.onNodeWithTag(BrowseTestTags.TAB_TOP_LIVE).assertIsNotDisplayed()
    }

    @Test
    fun pullingDownAtTheTopRefreshesInALandscapePhone() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(listOf<TopLiveIntent>(TopLiveIntent.Refresh), intents)
        rule.onNodeWithTag(BrowseTestTags.TAB_TOP_LIVE).assertIsDisplayed()
    }

    @Test
    fun aCollapsedHeaderComesBackBeforeAPullRefreshes() {
        show()
        rule.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).performTouchInput { swipeUp() }
        rule.onNodeWithTag(BrowseTestTags.TAB_TOP_LIVE).assertIsNotDisplayed()

        repeat(3) { rule.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).performTouchInput { swipeDown() } }
        rule.waitForIdle()

        rule.onNodeWithTag(BrowseTestTags.TAB_TOP_LIVE).assertIsDisplayed()
        assertEquals(TopLiveIntent.Refresh, intents.lastOrNull())
    }
}

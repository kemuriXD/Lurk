package io.github.kemurixd.lurk.featureui.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.search.SearchPhase
import io.github.kemurixd.lurk.feature.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search in a landscape phone, after rotation, and at 200% font. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class SearchLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val loaded =
        mapOf<Platform, PlatformSlice<Unit>>(
            Platform.TWITCH to PlatformSlice.Loaded(Unit),
            Platform.KICK to PlatformSlice.Loaded(Unit),
        )

    private fun results(count: Int = 40) =
        SearchUiState(
            query = "a",
            channels = (1..count).map { channel("channel%02d".format(it)) },
            slices = loaded,
            phase = SearchPhase.RESULTS,
        )

    private fun channel(login: String) =
        Channel(
            ref = ChannelRef(Platform.TWITCH, login),
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

    private fun show(
        state: SearchUiState,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LurkTheme(dynamicColor = false) {
                    ErrorHost(ErrorHandlers()) {
                        SearchScreen(state = state, onIntent = {}, onOpenChannel = {}, onOpenCategory = {})
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun inALandscapePhoneTheFiltersScrollAwayAndTheResultsAreReachable() {
        show(results())
        rule.onNodeWithTag(SearchTestTags.TYPE_ALL).assertIsDisplayed()
        rule.onNodeWithTag(SearchTestTags.LIST).performTouchInput { swipeUp() }
        rule.onNodeWithTag(SearchTestTags.TYPE_ALL).assertIsNotDisplayed()
        rule.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
    }

    @Test
    fun inPortraitTheFiltersStayPinned() {
        show(results())
        rule.onNodeWithTag(SearchTestTags.LIST).performTouchInput { swipeUp() }
        rule.onNodeWithTag(SearchTestTags.TYPE_ALL).assertIsDisplayed()
    }

    @Test
    fun theScrollPositionSurvivesRecreation() {
        val tester = StateRestorationTester(rule)
        val state = results()
        tester.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    SearchScreen(state = state, onIntent = {}, onOpenChannel = {}, onOpenCategory = {})
                }
            }
        }
        rule.onNodeWithTag(SearchTestTags.LIST).performScrollToNode(hasText("Channel30"))
        rule.onNodeWithText("Channel30").assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Channel30").assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
        return out.first()
    }

    @Test
    fun theTypeSegmentsKeepTheirWordsWholeAndTheirHeightAtDoubleFont() {
        show(results(count = 3), fontScale = 2f)
        val tags = listOf(SearchTestTags.TYPE_ALL, SearchTestTags.TYPE_CHANNELS, SearchTestTags.TYPE_CATEGORIES)
        val heights =
            tags.map { tag ->
                val segment = rule.onNodeWithTag(tag)
                val text =
                    rule.onNode(
                        hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true),
                        useUnmergedTree = true,
                    )
                val layout = text.layout()
                assertEquals("$tag wraps", 1, layout.lineCount)
                assertTrue("$tag is cut off", !layout.isLineEllipsized(0))
                segment.fetchSemanticsNode().boundsInRoot.height
            }
        assertEquals("segment heights $heights", 1, heights.distinct().size)
    }
}

package io.github.kemurixd.lurk.featureui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformSlice
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
    @Config(qualifiers = "w800dp-h360dp")
    fun theFiltersComeBackWhenACollapsedSearchFindsNothing() {
        var state by mutableStateOf(results())
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    SearchScreen(state = state, onIntent = {}, onOpenChannel = {}, onOpenCategory = {})
                }
            }
        }
        rule.onNodeWithTag(SearchTestTags.LIST).performTouchInput { swipeUp() }
        rule.onNodeWithTag(SearchTestTags.TYPE_ALL).assertIsNotDisplayed()
        state = SearchUiState(query = "zzqxv", phase = SearchPhase.NO_RESULTS)
        rule.waitForIdle()
        rule.onNodeWithTag(SearchTestTags.TYPE_ALL).assertIsDisplayed()
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

    private fun segmentTops(): List<Float> =
        listOf(SearchTestTags.TYPE_ALL, SearchTestTags.TYPE_CHANNELS, SearchTestTags.TYPE_CATEGORIES).map {
            rule
                .onNodeWithTag(it)
                .fetchSemanticsNode()
                .boundsInRoot.top
        }

    private fun assertStacked(stacked: Boolean) {
        val tops = segmentTops()
        assertEquals("segment tops $tops", if (stacked) 3 else 1, tops.distinct().size)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun threeSegmentsShareARowWhenTheirWordsFit() {
        show(results(count = 3), fontScale = 1f)
        assertStacked(false)
    }

    @Test
    @Config(qualifiers = "w320dp-h780dp")
    fun threeSegmentsShareARowOnANarrowPhoneAtNormalType() {
        show(results(count = 3), fontScale = 1f)
        assertStacked(false)
    }

    @Test
    @Config(qualifiers = "w320dp-h780dp")
    fun theSegmentsStackOnANarrowPhoneAtOneAndAFifthType() {
        show(results(count = 3), fontScale = 1.15f)
        assertStacked(true)
    }

    @Test
    fun theSegmentsStackAtDoubleFont() {
        show(results(count = 3), fontScale = 2f)
        assertStacked(true)
    }

    @Test
    fun stackedSegmentsStayOneRadioGroupOfThree() {
        show(results(count = 3), fontScale = 2f)
        val tags = listOf(SearchTestTags.TYPE_ALL, SearchTestTags.TYPE_CHANNELS, SearchTestTags.TYPE_CATEGORIES)
        val group =
            rule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.config.contains(SemanticsProperties.CollectionInfo) }
        assertEquals("one group holding the three segments", 1, group.size)
        assertEquals(3, group.single().config[SemanticsProperties.CollectionInfo].rowCount)
        tags.forEachIndexed { index, tag ->
            val config = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertEquals(Role.RadioButton, config[SemanticsProperties.Role])
            assertEquals(index, config[SemanticsProperties.CollectionItemInfo].rowIndex)
        }
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

    @Test
    @Config(qualifiers = "w420dp-h780dp")
    fun aSelectedCategoriesSegmentIsNeverCutOffWhereItsWordOnlyJustFits() {
        var width by mutableStateOf(420.dp)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.15f)) {
                LurkTheme(dynamicColor = false) {
                    Box(Modifier.width(width)) { TypeSwitch(selected = SearchType.CATEGORIES, onSelect = {}) }
                }
            }
        }
        // Down through the band where "Categories" fits but "Categories" and the check icon don't.
        for (dp in 420 downTo 300 step 4) {
            width = dp.dp
            rule.waitForIdle()
            val text =
                rule.onNode(
                    hasAnyAncestor(hasTestTag(SearchTestTags.TYPE_CATEGORIES)) and hasText("Categories"),
                    useUnmergedTree = true,
                )
            val segment = rule.onNodeWithTag(SearchTestTags.TYPE_CATEGORIES).fetchSemanticsNode().boundsInRoot
            val word = text.fetchSemanticsNode().boundsInRoot
            assertTrue("Categories is cut off at $dp dp", !text.layout().isLineEllipsized(0))
            // Clear of the check icon (12 dp padding, 18 dp icon, 8 dp gap) and of the border (12 dp),
            // give or take 1 dp.
            assertTrue("Categories crowds the check icon at $dp dp", word.left - segment.left >= 37f)
            assertTrue("Categories touches the border at $dp dp", segment.right - word.right >= 11f)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun inALandscapePhoneTheNoResultsMessageCanBeRead() {
        show(SearchUiState(query = "zzqxv", phase = SearchPhase.NO_RESULTS))
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        for (line in listOf("No results for “zzqxv”", "Check the spelling, or try a category name instead.")) {
            val node = rule.onNodeWithText(line)
            node.performScrollTo()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$line ends at ${bounds.bottom}, below the window at ${root.bottom}",
                bounds.bottom <= root.bottom,
            )
            assertTrue("$line starts at ${bounds.top}, above the window", bounds.top >= root.top)
        }
        rule.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
    }
}

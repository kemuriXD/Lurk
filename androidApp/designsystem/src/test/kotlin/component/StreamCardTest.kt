package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamCardTest {
    @get:Rule val rule = createComposeRule()

    private var player = 0
    private var channel = 0

    private fun show() =
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = { player++ }, onOpenChannel = { channel++ })
            }
        }

    @After
    fun resetImageLoader() = SingletonImageLoader.reset()

    private fun tap(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    @Test
    fun streamCardAvatarOpensChannel() {
        show()
        tap("stream_card_avatar")
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun streamCardNameOpensChannel() {
        show()
        tap("stream_card_name")
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun streamCardBodyOpensPlayer() {
        show()
        tap("stream_card_title")
        assertEquals(0 to 1, channel to player)
    }

    @Test
    fun channelControlsHaveAtLeast48dpTouchTargets() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
            }
        }
        // Compose widens small pointer targets to 48 dp; this pins that the layout keeps relying on it.
        val min = 48 * density - 0.5f
        val avatar =
            rule
                .onNodeWithTag(
                    "stream_card_avatar",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .touchBoundsInRoot
        assertTrue("avatar ${avatar.width}x${avatar.height}", avatar.width >= min && avatar.height >= min)
        val name = rule.onNodeWithTag("stream_card_name", useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
        assertTrue("name height ${name.height}", name.height >= min)
    }

    @Test
    fun theCardOffersOneAccessibilityStopPerAction() {
        show()
        // TalkBack sees the merged tree: the card opens the player and the labelled avatar opens the channel.
        // The name opens the channel for touch, but gives accessibility no second stop for it.
        val seenByAccessibility =
            hasClickAction() and !SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
        rule.onAllNodes(seenByAccessibility).assertCountEquals(2)
        rule.onAllNodesWithContentDescription("Open Streamer One").assertCountEquals(1)
    }

    @Test
    fun placeholderCardNotClickable() {
        rule.setContent { LurkTheme(dynamicColor = false) { StreamCardPlaceholder() } }
        rule.onNodeWithTag("stream_card_placeholder", useUnmergedTree = true).assertHasNoClickAction()
    }

    @Test
    fun aPreviewThatLandsDoesNotRecomposeItsThumbnail() {
        val landed = CompletableDeferred<Unit>()
        var asked = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(ApplicationProvider.getApplicationContext())
                .components {
                    add(
                        Interceptor { chain ->
                            asked++
                            landed.await()
                            SuccessResult(ColorImage(), chain.request)
                        },
                    )
                }.build(),
        )
        var viewerReads = 0
        rule.setContent {
            CountingStrings(onRead = { id -> if (id == R.string.viewers_count) viewerReads++ }) {
                LurkTheme(dynamicColor = false) {
                    StreamThumbnail(
                        testStream().copy(thumbnailUrl = "https://cdn.example/t/1.jpg"),
                        Modifier.size(160.dp, 90.dp),
                    )
                }
            }
        }
        rule.waitUntil { asked > 0 }
        rule.waitForIdle()
        viewerReads = 0
        landed.complete(Unit)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        // The thumbnail asks for its viewer count each time it recomposes. A preview landing changes nothing it shows.
        assertEquals("thumbnail recompositions after the preview landed", 0, viewerReads)
        assertEquals("requests", 1, asked)
    }

    @Test
    fun aRefreshedPreviewLoadsOverTheOneShown() {
        val placeholders = mutableListOf<String?>()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(ApplicationProvider.getApplicationContext())
                .components {
                    add(
                        Interceptor { chain ->
                            placeholders += chain.request.placeholderMemoryCacheKey?.key
                            SuccessResult(ColorImage(), chain.request)
                        },
                    )
                }.build(),
        )
        var stream by mutableStateOf(testStream().copy(thumbnailUrl = "https://cdn.example/t/1.jpg?t=1"))
        rule.setContent {
            LurkTheme(dynamicColor = false) { StreamThumbnail(stream, Modifier.size(160.dp, 90.dp)) }
        }
        rule.waitUntil { placeholders.size == 1 }
        stream = stream.copy(viewers = 13_000)
        rule.waitForIdle()
        stream = stream.copy(thumbnailUrl = "https://cdn.example/t/1.jpg?t=2")
        rule.waitForIdle()
        rule.waitUntil { placeholders.size >= 2 }
        rule.waitForIdle()
        // A new viewer count reuses the request; a new URL loads with the preview already shown as its placeholder.
        assertEquals(listOf(null, "https://cdn.example/t/1.jpg?t=1"), placeholders)
    }

    private fun showAt(
        width: Int,
        fontScale: Float,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LurkTheme(dynamicColor = false) {
                    Box(Modifier.width(width.dp)) {
                        StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
                    }
                }
            }
        }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun aNarrowCardStacksTheTextUnderThePictureSoTheNameStaysReadable() {
        // A 320 dp phone at 1.15x type: the card is 288 dp wide.
        showAt(width = 288, fontScale = 1.15f)
        val picture = bounds(StreamCardTags.VIEWERS)
        val name = bounds("stream_card_name")
        assertTrue("name top ${name.top} is beside the picture (bottom ${picture.bottom})", name.top >= picture.bottom)
        val layout = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithTag("stream_card_name", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layout)
        assertFalse("the name is cut off", layout.single().isLineEllipsized(0))
    }

    @Test
    fun aCardOnA360dpPhoneKeepsThePictureBesideTheText() {
        showAt(width = 328, fontScale = 1f)
        val picture = bounds(StreamCardTags.VIEWERS)
        val name = bounds("stream_card_name")
        assertTrue("name top ${name.top} is under the picture (bottom ${picture.bottom})", name.top < picture.bottom)
    }
}

package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.designsystem.time.LocalClock
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpandedStreamCardTest {
    @get:Rule val rule = createComposeRule()

    private var player = 0
    private var channel = 0
    private var now = Instant.parse("2026-11-13T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun live(
        startedAt: Instant? = now - (1.hours + 47.minutes + 59.seconds),
        tags: List<String> = emptyList(),
    ): Stream =
        testStream().copy(
            viewers = 20_052,
            category = Category(Platform.TWITCH, "1", "Just Chatting", null),
            startedAt = startedAt,
            tags = tags,
        )

    private fun show(stream: Stream = live()) =
        rule.setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                LurkTheme(dynamicColor = false) {
                    LayoutHost(Phone, 1f) {
                        ExpandedStreamCard(stream, onOpenPlayer = { player++ }, onOpenChannel = { channel++ })
                    }
                }
            }
        }

    private fun tap(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    /** What TalkBack reads on the card: the texts merged into its one node. */
    private fun cardTexts(): List<String> =
        rule
            .onNodeWithTag(ExpandedStreamCardTags.CARD)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.Text) { emptyList() }
            .map { it.text }

    /** The chips on screen, in order, "+N" included. */
    private fun shownTags(): List<String> =
        rule
            .onNodeWithTag(ExpandedStreamCardTags.TAGS, useUnmergedTree = true)
            .onChildren()
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .map { it.text }

    private fun cardDescriptions(): List<String> =
        rule
            .onNodeWithTag(ExpandedStreamCardTags.CARD)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

    @Test
    fun theCardIsReadAsOneSentenceWithTheChannelName() {
        show(live(tags = listOf("English", "Chill")))
        assertEquals(
            listOf(
                "Streamer One, Speedrunning all night, live for 1 hour 47 minutes, 20,052 viewers, Just Chatting, " +
                    "English, Chill, Twitch",
            ),
            cardDescriptions(),
        )
        assertEquals(emptyList<String>(), cardTexts())
    }

    @Test
    fun theSpokenUptimeLeavesOutWhatIsZero() {
        show(live(startedAt = now - 5.minutes))
        assertTrue(cardDescriptions().toString(), "live for 5 minutes" in cardDescriptions().single())
    }

    @Test
    fun aStreamUnderAMinuteOldIsSpokenAsJustStarted() {
        show(live(startedAt = now - 20.seconds))
        assertTrue(cardDescriptions().toString(), "live for less than a minute" in cardDescriptions().single())
    }

    @Test
    fun anUnknownStartLeavesOutTheLiveFor() {
        show(live(startedAt = null))
        assertTrue(cardDescriptions().toString(), "live for" !in cardDescriptions().single())
    }

    @Test
    fun theSpokenUptimeKeepsUpEachMinute() {
        show(live(startedAt = now - 59.minutes))
        assertTrue("live for 59 minutes" in cardDescriptions().single())
        now += 2.minutes
        rule.mainClock.advanceTimeBy(60_100)
        assertTrue(cardDescriptions().toString(), "live for 1 hour 1 minute," in cardDescriptions().single())
    }

    @Test
    fun avatarOpensChannel() {
        show()
        tap(ExpandedStreamCardTags.AVATAR)
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun nameOpensChannel() {
        show()
        tap(ExpandedStreamCardTags.NAME)
        assertEquals(1 to 0, channel to player)
    }

    @Test
    fun previewOpensPlayer() {
        show()
        tap(ExpandedStreamCardTags.PREVIEW)
        assertEquals(0 to 1, channel to player)
    }

    @Test
    fun titleOpensPlayer() {
        show()
        // Its far end: a short name's widened touch target may reach over the title's start.
        rule.onNodeWithTag(ExpandedStreamCardTags.TITLE, useUnmergedTree = true).performTouchInput {
            click(centerRight - Offset(1f, 0f))
        }
        assertEquals(0 to 1, channel to player)
    }

    @Test
    fun channelControlsHaveAtLeast48dpTouchTargets() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            LurkTheme(dynamicColor = false) { ExpandedStreamCard(live(), onOpenPlayer = {}, onOpenChannel = {}) }
        }
        val min = 48 * density - 0.5f
        for (tag in listOf(ExpandedStreamCardTags.AVATAR, ExpandedStreamCardTags.NAME)) {
            val box = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$tag ${box.width}x${box.height}", box.width >= min && box.height >= min)
        }
    }

    @Test
    fun theCardOffersOneAccessibilityStopPerAction() {
        show()
        val seenByAccessibility =
            hasClickAction() and !SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
        rule.onAllNodes(seenByAccessibility).assertCountEquals(2)
        rule.onAllNodesWithContentDescription("Open Streamer One").assertCountEquals(1)
    }

    @Test
    fun theViewersAreSaidOnceAndTheCountIsSilent() {
        show()
        assertEquals(1, Regex("20,052").findAll(cardDescriptions().single()).count())
        assertEquals(emptyList<String>(), cardTexts())
    }

    @Test
    fun metaLineShowsUptimeAndCategory() {
        show()
        rule.onNodeWithTag(ExpandedStreamCardTags.UPTIME, useUnmergedTree = true).assertTextEquals("1:47:59")
        rule.onNodeWithText("Just Chatting", useUnmergedTree = true).assertExists()
    }

    @Test
    fun uptimeTicksEverySecond() {
        show()
        now += 1.seconds
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithTag(ExpandedStreamCardTags.UPTIME, useUnmergedTree = true).assertTextEquals("1:48:00")
    }

    @Test
    fun anUnknownStartHidesTheUptime() {
        show(live(startedAt = null))
        rule.onNodeWithTag(ExpandedStreamCardTags.UPTIME, useUnmergedTree = true).assertDoesNotExist()
        assertTrue(cardDescriptions().toString(), "20,052 viewers" in cardDescriptions().single())
    }

    @Test
    fun fewTagsAllShowWithNoMoreChip() {
        show(live(tags = listOf("English", "Chill")))
        assertEquals(listOf("English", "Chill"), shownTags())
        rule.onNodeWithTag(ExpandedStreamCardTags.MORE_TAGS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tagsThatDontFitEndWithAMoreChip() {
        val tags = (1..10).map { "Quite a long tag number $it" }
        show(live(tags = tags))
        val shown = tags.count { it in shownTags() }
        assertTrue("some tags show, shown $shown", shown in 1 until tags.size)
        rule
            .onNodeWithTag(ExpandedStreamCardTags.MORE_TAGS, useUnmergedTree = true)
            .assertTextEquals("+${tags.size - shown}")
    }

    @Test
    fun noTagsMeansNoTagsRow() {
        show(live(tags = emptyList()))
        rule.onNodeWithTag(ExpandedStreamCardTags.TAGS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBadgeNamesThePlatform() {
        show(live().copy(channel = live().channel.copy(platform = Platform.KICK)))
        assertTrue(cardDescriptions().toString(), cardDescriptions().single().endsWith(", Kick"))
    }

    @Test
    fun placeholderIsNotClickable() {
        rule.setContent { LurkTheme(dynamicColor = false) { ExpandedStreamCardPlaceholder() } }
        rule.onNodeWithTag(ExpandedStreamCardTags.PLACEHOLDER, useUnmergedTree = true).assertHasNoClickAction()
    }
}

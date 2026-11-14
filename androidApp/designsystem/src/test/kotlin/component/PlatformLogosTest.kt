package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformLogosTest {
    @get:Rule val rule = createComposeRule()

    private fun logos(platform: Platform) = rule.onAllNodesWithTag(PlatformTags.logo(platform), useUnmergedTree = true)

    private fun badges() = rule.onAllNodesWithTag(PlatformTags.BADGE, useUnmergedTree = true)

    private fun channel() =
        Channel(
            ref = ChannelRef(Platform.KICK, "streamer1"),
            id = "1000001",
            chatId = "streamer1",
            displayName = "Streamer One",
            avatarUrl = null,
            bannerUrl = null,
            bio = null,
            followers = null,
            live = testStream(Platform.KICK),
            lastStream = null,
        )

    @Test
    fun everyPlatformChipHasItsLogoWhicheverIsSelected() {
        var selected by mutableStateOf(PlatformFilter.ALL)
        rule.setContent { LurkTheme(dynamicColor = false) { PlatformFilterChips(selected, onSelect = {}) } }
        for (filter in PlatformFilter.entries) {
            selected = filter
            rule.waitForIdle()
            logos(Platform.TWITCH).assertCountEquals(1)
            logos(Platform.KICK).assertCountEquals(1)
        }
    }

    @Test
    fun selectedPlatformChipKeepsItsLogo() {
        rule.setContent { LurkTheme(dynamicColor = false) { PlatformFilterChips(PlatformFilter.KICK, onSelect = {}) } }
        rule.onNodeWithText("Kick").assertIsSelected()
        logos(Platform.KICK).assertCountEquals(1)
    }

    @Test
    fun cardsShowTheirBadgeByDefault() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
                ExpandedStreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
                ChannelRow(channel(), onClick = {})
                CategoryTile(Category(Platform.TWITCH, "1", "Retro Racers", null), onClick = {})
            }
        }
        badges().assertCountEquals(4)
    }

    @Test
    fun cardsHideTheirBadgeWhenAskedTo() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                StreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {}, showPlatform = false)
                ExpandedStreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {}, showPlatform = false)
                ChannelRow(channel(), onClick = {}, showPlatform = false)
                CategoryTile(Category(Platform.TWITCH, "1", "Retro Racers", null), onClick = {}, showPlatform = false)
            }
        }
        badges().assertCountEquals(0)
    }

    @Test
    fun expandedCardNamesThePlatformWhileItsBadgeShows() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ExpandedStreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {})
            }
        }
        val spoken =
            rule
                .onNodeWithTag(ExpandedStreamCardTags.CARD)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString()
        assertTrue(spoken, spoken.endsWith("Twitch"))
    }

    @Test
    fun hiddenBadgeLeavesPlatformOutOfTheSpokenCard() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ExpandedStreamCard(testStream(), onOpenPlayer = {}, onOpenChannel = {}, showPlatform = false)
            }
        }
        val spoken =
            rule
                .onNodeWithTag(ExpandedStreamCardTags.CARD)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString()
        assertTrue(spoken, !spoken.contains("Twitch"))
    }

    @Test
    fun signInActionCarriesThePlatformLogo() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    ErrorBanner("Couldn’t load Kick", LurkError.NotSignedIn, onRetry = {}, platform = Platform.KICK)
                    ErrorState("Couldn’t load Twitch", LurkError.AuthExpired, onRetry = {}, platform = Platform.TWITCH)
                }
            }
        }
        logos(Platform.KICK).assertCountEquals(1)
        logos(Platform.TWITCH).assertCountEquals(1)
    }

    @Test
    fun retryActionHasNoLogo() {
        rule.setContent {
            LurkTheme(dynamicColor = false) {
                ErrorHost(ErrorHandlers()) {
                    ErrorBanner("Couldn’t load Kick", LurkError.Timeout, onRetry = {}, platform = Platform.KICK)
                }
            }
        }
        logos(Platform.KICK).assertCountEquals(0)
    }
}

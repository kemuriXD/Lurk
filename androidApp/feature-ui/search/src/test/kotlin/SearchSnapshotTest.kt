package io.github.kemurixd.lurk.featureui.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.search.SearchPhase
import io.github.kemurixd.lurk.feature.search.SearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Baselines for Search, named after the Figma frames (Search / Search / State / Theme). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class SearchSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val bothLoaded =
        mapOf<Platform, PlatformSlice<Unit>>(
            Platform.TWITCH to PlatformSlice.Loaded(Unit),
            Platform.KICK to PlatformSlice.Loaded(Unit),
        )

    private fun show(
        name: String,
        state: SearchUiState,
        largeFont: Boolean = false,
    ) = rule.snapshots(name, largeFont) {
        SearchScreen(state = state, onIntent = {}, onOpenChannel = {}, onOpenCategory = {})
    }

    @Test
    fun empty() = show("Search_Empty", SearchUiState())

    @Test
    fun typing() = show("Search_Typing", SearchUiState(query = "pix", phase = SearchPhase.TYPING))

    @Test
    fun mainResults() = show("Search_Results", resultsState(), largeFont = true)

    @Test
    fun partialFailure() =
        show(
            "Search_PartialFailure_Twitch",
            resultsState(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Loaded(Unit),
                    ),
            ),
        )

    @Test
    fun retryInProgress() =
        show(
            "Search_PartialFailure_RetryInProgress",
            resultsState(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Offline),
                        Platform.KICK to PlatformSlice.Loaded(Unit),
                    ),
            ).copy(retrying = setOf(Platform.TWITCH)),
        )

    @Test
    fun failedBoth() =
        show(
            "Search_Failed",
            SearchUiState(
                query = "pixel",
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Timeout),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Timeout),
                    ),
                phase = SearchPhase.RESULTS,
            ),
        )

    @Test
    fun noResults() = show("Search_NoResults", SearchUiState(query = "zzqxv", phase = SearchPhase.NO_RESULTS))

    private fun resultsState(slices: Map<Platform, PlatformSlice<Unit>> = bothLoaded) =
        SearchUiState(
            query = "pixel",
            channels =
                listOf(
                    liveChannel("pixelotter", Platform.TWITCH),
                    liveChannel("pixelfox", Platform.KICK),
                    channel("pixelsleeper", Platform.TWITCH).copy(lastStream = LastStream(null, null, now - 3.days)),
                    channel("pixelghost", Platform.KICK),
                ),
            categories =
                listOf(
                    Category(Platform.TWITCH, "1", "Pixel Quest", null, 8_400),
                    Category(Platform.KICK, "2", "Pixel Racers", null, 900),
                ),
            slices = slices,
            phase = SearchPhase.RESULTS,
        )

    private fun channel(
        login: String,
        platform: Platform,
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

    private fun liveChannel(
        login: String,
        platform: Platform,
    ) = channel(login, platform).copy(
        live =
            Stream(
                channel = ChannelRef(platform, login),
                channelId = "1000001",
                displayName = login.replaceFirstChar { it.uppercase() },
                title = "Speedrunning all night",
                category = Category(platform, "1", "Retro Racers", null),
                viewers = 1_200,
                startedAt = null,
                thumbnailUrl = null,
                avatarUrl = null,
            ),
    )
}

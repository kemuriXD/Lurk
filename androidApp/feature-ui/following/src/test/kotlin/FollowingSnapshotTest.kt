package io.github.kemurixd.lurk.featureui.following

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.following.FollowingSort
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Baselines for the Following screen, named after the Figma frames (Following / Following / State / Theme). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_DEVICE)
class FollowingSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun show(
        name: String,
        state: FollowingUiState,
        largeFont: Boolean = false,
    ) = rule.snapshots(name, largeFont) {
        FollowingScreen(
            state = state,
            onIntent = {},
            onOpenPlayer = {},
            onOpenChannel = {},
            onSignIn = {},
            onOpenBrowse = {},
        )
    }

    @Test
    fun signedIn() = show("Following_SignedIn", signedInState(), largeFont = true)

    @Test
    @Config(qualifiers = SNAPSHOT_TALL_DEVICE)
    fun signedInFullLength() = show("Following_SignedIn_FullLength", signedInState())

    @Test
    @Config(qualifiers = SNAPSHOT_TALL_DEVICE)
    fun signedInOfflineCollapsed() =
        show("Following_SignedIn_OfflineCollapsed", signedInState().copy(offlineCollapsed = true))

    @Test
    fun signedOut() =
        // Kick's slice comes first on purpose: Twitch's sign-in button must still lead.
        show(
            "Following_SignedOut",
            state(
                slices =
                    linkedMapOf(
                        Platform.KICK to PlatformSlice.NotSignedIn,
                        Platform.TWITCH to PlatformSlice.NotSignedIn,
                    ),
            ),
        )

    @Test
    fun signedInToOnePlatform() =
        show(
            "Following_SignedIn_KickSignedOut",
            state(
                live = lives(Platform.TWITCH),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Followed(lives(Platform.TWITCH), emptyList())),
                        Platform.KICK to PlatformSlice.NotSignedIn,
                    ),
            ),
        )

    @Test
    fun partialFailureKick() = show("Following_PartialFailure_Kick", partialFailure())

    @Test
    fun partialFailureMerged() {
        val twitch = lives(Platform.TWITCH)
        val kick = lives(Platform.KICK)
        show(
            "Following_PartialFailure_Merged",
            state(
                live = twitch + kick,
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Followed(twitch, emptyList()), stale = true),
                        Platform.KICK to PlatformSlice.Loaded(Followed(kick, emptyList()), stale = true),
                    ),
            ).copy(refreshErrors = mapOf(Platform.TWITCH to LurkError.Offline, Platform.KICK to LurkError.Offline)),
        )
    }

    @Test
    fun failedBoth() =
        show(
            "Following_Failed",
            state(
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Failed(LurkError.Timeout),
                        Platform.KICK to PlatformSlice.Failed(LurkError.Timeout),
                    ),
            ),
        )

    @Test
    fun retryInProgress() =
        show("Following_PartialFailure_RetryInProgress", partialFailure().copy(retrying = setOf(Platform.KICK)))

    @Test
    fun loading() =
        show(
            "Following_Loading",
            state(slices = mapOf(Platform.TWITCH to PlatformSlice.Loading, Platform.KICK to PlatformSlice.Loading))
                .copy(refreshing = true),
        )

    @Test
    fun nobodyLive() =
        show(
            "Following_Empty_NobodyLive",
            state(
                offline = offline(),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Followed(emptyList(), offline())),
                        Platform.KICK to PlatformSlice.Loaded(Followed(emptyList(), emptyList())),
                    ),
            ),
        )

    @Test
    fun stressLongText() {
        val long =
            stream(
                "averyveryverylongchannelname",
                Platform.TWITCH,
                "An extremely long stream title that keeps going well past what two lines can show",
            )
        show(
            "Following_Stress_LongText",
            state(
                live = listOf(long),
                offline =
                    listOf(
                        channel("averyveryverylongchannelname", Platform.KICK).copy(
                            displayName = "A very very very long channel name that cannot fit",
                        ),
                    ),
                slices =
                    mapOf(
                        Platform.TWITCH to PlatformSlice.Loaded(Followed(listOf(long), emptyList())),
                        Platform.KICK to PlatformSlice.Loaded(Followed(emptyList(), emptyList())),
                    ),
            ),
        )
    }

    private fun partialFailure(): FollowingUiState {
        val twitch = lives(Platform.TWITCH)
        return state(
            live = twitch,
            slices =
                mapOf(
                    Platform.TWITCH to PlatformSlice.Loaded(Followed(twitch, emptyList())),
                    Platform.KICK to PlatformSlice.Failed(LurkError.Offline),
                ),
        )
    }

    private fun signedInState(): FollowingUiState {
        val twitch = lives(Platform.TWITCH)
        val kick = lives(Platform.KICK)
        val gone = offline()
        return state(
            live = (twitch + kick).sortedByDescending { it.viewers },
            offline = gone,
            slices =
                mapOf(
                    Platform.TWITCH to PlatformSlice.Loaded(Followed(twitch, gone)),
                    Platform.KICK to PlatformSlice.Loaded(Followed(kick, emptyList())),
                ),
        )
    }

    private fun state(
        live: List<Stream> = emptyList(),
        offline: List<Channel> = emptyList(),
        slices: Map<Platform, PlatformSlice<Followed>>,
    ) = FollowingUiState(
        filter = PlatformFilter.ALL,
        sort = FollowingSort.VIEWERS,
        live = live,
        offline = offline,
        slices = slices,
        refreshing = false,
    )

    private fun lives(platform: Platform): List<Stream> =
        if (platform == Platform.TWITCH) {
            listOf(
                stream("streamer1", platform, "Speedrunning all night", viewers = 12_400, since = 150),
                stream("streamer2", platform, "Learning to knit, live", viewers = 3_200, since = 45),
            )
        } else {
            listOf(stream("streamer3", platform, "Cozy building session", viewers = 5_100, since = 300))
        }

    private fun offline(): List<Channel> =
        listOf(
            channel("sleeper1", Platform.TWITCH).copy(lastStream = LastStream(null, null, now - 3.days)),
            channel("sleeper2", Platform.KICK).copy(lastStream = LastStream(null, null, now - 5.hours)),
            channel("sleeper3", Platform.TWITCH),
        )

    private fun stream(
        login: String,
        platform: Platform,
        title: String,
        viewers: Long = 1_200,
        since: Int = 90,
    ) = Stream(
        channel = ChannelRef(platform, login),
        channelId = "1000001",
        displayName = login.replaceFirstChar { it.uppercase() },
        title = title,
        category = Category(platform, "1", "Retro Racers", null),
        viewers = viewers,
        startedAt = now - since.minutes,
        thumbnailUrl = null,
        avatarUrl = null,
        tags = listOf("English"),
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
}

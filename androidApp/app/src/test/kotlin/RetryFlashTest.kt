package io.github.kemurixd.lurk

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.ErrorTags
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.accountsModule
import io.github.kemurixd.lurk.feature.browse.browseModule
import io.github.kemurixd.lurk.feature.channel.channelModule
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.following.followingModule
import io.github.kemurixd.lurk.feature.search.searchModule
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.feature.testing.testStream
import io.github.kemurixd.lurk.featureui.browse.BrowseTestTags
import io.github.kemurixd.lurk.login.CookieJar
import io.github.kemurixd.lurk.navigation.rememberNavigator
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

private object NoCookies : CookieJar {
    override fun read(url: String): String? = null

    override suspend fun clearAll() = Unit
}

/**
 * Retry through the real view models and the real shell. The platforms answer when a test says so, and the compose
 * clock is stepped frame by frame, so a swap to a skeleton or a vanished error, even for one frame, fails the test.
 * Offline answers within a frame or two; waiting on a gate makes each of those frames observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RetryFlashTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)

    private val sessions =
        SessionRepository(
            InMemorySecureStore(),
            listOf(FakeProfileFetcher(Platform.TWITCH), FakeProfileFetcher(Platform.KICK)),
            scope,
        )

    @Before
    fun startKoinWithFakes() {
        startKoin {
            modules(
                followingModule,
                browseModule,
                searchModule,
                channelModule,
                accountsModule,
                module {
                    single<CookieJar> { NoCookies }
                    single<CoroutineScope>(named("appScope")) { scope }
                    single { inMemorySettingsStore() }
                    single<DispatcherProvider> { TestDispatchers(Dispatchers.Unconfined) }
                    single { sessions }
                    single { AccountsState(get()) }
                    single { LastLiveLoader(get(), get()) }
                    single<List<StreamingPlatform>> { listOf(twitch, kick) }
                },
            )
        }
    }

    @After
    fun stopKoinAndScope() {
        stopKoin()
        scope.cancel()
    }

    private fun showShell() {
        compose.setContent {
            val navigator = rememberNavigator(io.github.kemurixd.lurk.navigation.TopLevel.BROWSE)
            LurkTheme { LurkApp(navigator) }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitErrorState() {
        compose.waitUntilAtLeastOneExists(
            androidx.compose.ui.test
                .hasTestTag(ErrorTags.STATE),
            timeoutMillis = 5_000,
        )
    }

    private fun skeletons() = compose.onAllNodesWithTag("stream_card_placeholder")

    private fun retryButton() =
        compose.onNode(
            hasText("Retry") and
                androidx.compose.ui.test
                    .hasClickAction(),
        )

    /** Steps [frames] frames of 16 ms and runs [check] after each. */
    private fun steppingFrames(
        frames: Int,
        check: () -> Unit,
    ) {
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            check()
        }
    }

    private fun theErrorStateStaysWithoutASkeleton() {
        skeletons().assertCountEquals(0)
        compose.onNodeWithTag(ErrorTags.STATE).assertIsDisplayed()
    }

    private fun failBoth(
        error: LurkError,
        gate: CompletableDeferred<Unit>? = null,
    ) {
        for (platform in listOf(twitch, kick)) {
            platform.topLive = {
                gate?.await()
                Outcome.Failure(error)
            }
        }
    }

    @Test
    fun retryFromTheFullScreenErrorNeverShowsTheSkeleton() {
        failBoth(LurkError.Offline)
        showShell()
        awaitErrorState()
        val gate = CompletableDeferred<Unit>()
        failBoth(LurkError.Offline, gate)
        retryButton().performScrollTo()
        compose.mainClock.autoAdvance = false

        retryButton().performClick()
        // The request is still out: the error stays and the button keeps its live look for the first 300 ms.
        steppingFrames(8) {
            theErrorStateStaysWithoutASkeleton()
            retryButton().assertIsEnabled()
        }
        compose.runOnIdle { gate.complete(Unit) }
        // It fails again: still no skeleton, in any frame.
        steppingFrames(8) { theErrorStateStaysWithoutASkeleton() }

        retryButton().assertIsEnabled()
        compose.onAllNodesWithTag(ErrorTags.RETRY_PROGRESS).assertCountEquals(0)
    }

    @Test
    fun aRetryThatTakesAWhileShowsAProgressSpinnerInTheButton() {
        failBoth(LurkError.Offline)
        showShell()
        awaitErrorState()
        failBoth(LurkError.Offline, CompletableDeferred())
        retryButton().performScrollTo()
        compose.mainClock.autoAdvance = false

        retryButton().performClick()
        steppingFrames(10) { compose.onAllNodesWithTag(ErrorTags.RETRY_PROGRESS).assertCountEquals(0) }
        compose.mainClock.advanceTimeBy(300)

        compose.onAllNodesWithTag(ErrorTags.RETRY_PROGRESS).assertCountEquals(1)
        theErrorStateStaysWithoutASkeleton()
        retryButton().assertIsNotEnabled()
    }

    @Test
    fun aRetryThatSucceedsReplacesTheErrorWithTheStreams() {
        failBoth(LurkError.Offline)
        showShell()
        awaitErrorState()
        val gate = CompletableDeferred<Unit>()
        for (platform in listOf(twitch, kick)) {
            platform.topLive = {
                gate.await()
                Outcome.Success(
                    Page(listOf(testStream(platform.id, login = "streamer1", displayName = "Streamer One")), null),
                )
            }
        }
        retryButton().performScrollTo()
        compose.mainClock.autoAdvance = false

        retryButton().performClick()
        steppingFrames(4) { theErrorStateStaysWithoutASkeleton() }
        compose.runOnIdle { gate.complete(Unit) }
        steppingFrames(4) { skeletons().assertCountEquals(0) }

        compose.onNodeWithTag(ErrorTags.STATE).assertDoesNotExist()
        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.onAllNodesWithText("Streamer One").assertCountEquals(2)
    }

    @Test
    fun retryFromABannerKeepsTheBannerAndTheListAbove() {
        twitch.topLive =
            {
                Outcome.Success(
                    Page(listOf(testStream(Platform.TWITCH, login = "streamer1", displayName = "Streamer One")), null),
                )
            }
        kick.topLive = { Outcome.Failure(LurkError.Offline) }
        showShell()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(ErrorTags.BANNER).fetchSemanticsNodes().isNotEmpty()
        }
        val gate = CompletableDeferred<Unit>()
        kick.topLive = {
            gate.await()
            Outcome.Failure(LurkError.Offline)
        }
        retryButton().performScrollTo()
        compose.mainClock.autoAdvance = false

        retryButton().performClick()
        steppingFrames(8) {
            skeletons().assertCountEquals(0)
            compose.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
            compose.onNodeWithText("Streamer One").assertExists()
            retryButton().assertIsEnabled()
        }
        compose.runOnIdle { gate.complete(Unit) }
        steppingFrames(8) {
            skeletons().assertCountEquals(0)
            compose.onNodeWithTag(ErrorTags.BANNER).assertIsDisplayed()
        }
        retryButton().assertIsEnabled()
    }
}

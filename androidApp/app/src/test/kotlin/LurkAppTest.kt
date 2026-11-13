package io.github.kemurixd.lurk

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.following.followingModule
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.navigation.BrowseRoute
import io.github.kemurixd.lurk.navigation.ChannelRoute
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.navigation.TopLevel
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.kemurixd.lurk.designsystem.R as DesignR

// A plain Application keeps LurkApplication's real Koin graph and startup work out of the test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LurkAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

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
                module {
                    single { inMemorySettingsStore() }
                    single<DispatcherProvider> { TestDispatchers(Dispatchers.Unconfined) }
                    single { sessions }
                    single { AccountsState(get()) }
                    single<List<StreamingPlatform>> {
                        listOf(FakeStreamingPlatform(Platform.TWITCH), FakeStreamingPlatform(Platform.KICK))
                    }
                },
            )
        }
    }

    @After
    fun stopKoinAndScope() {
        stopKoin()
        scope.cancel()
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = compose.activity.getString(id, *args)

    private fun tab(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    /** Shows the shell over a back stack the test can push onto, starting on Browse. */
    private fun showShellWithStack(): NavBackStack<NavKey> {
        lateinit var backStack: NavBackStack<NavKey>
        compose.setContent {
            backStack = rememberNavBackStack(BrowseRoute)
            LurkTheme { LurkApp(backStack) }
        }
        return backStack
    }

    @Test
    fun tabSelectionSwitchesDestination() {
        compose.setContent { LurkTheme { LurkApp() } }
        compose.onNodeWithText(string(R.string.placeholder_browse)).assertIsDisplayed()

        tab(R.string.tab_following).performClick()
        compose.onNodeWithTag(FOLLOWING_LIST).assertIsDisplayed()

        tab(R.string.tab_search).performClick()
        compose.onNodeWithText(string(R.string.placeholder_search)).assertIsDisplayed()
        compose.onNodeWithTag(FOLLOWING_LIST).assertDoesNotExist()
    }

    @Test
    fun accountButtonOpensSettings() {
        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).performClick()

        compose.onNodeWithText(string(R.string.placeholder_settings)).assertIsDisplayed()
    }

    @Test
    fun backFromChannelReturnsToOrigin() {
        val backStack = showShellWithStack()
        tab(R.string.tab_search).performClick()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.onNodeWithText(string(R.string.placeholder_channel)).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText(string(R.string.placeholder_search)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.placeholder_channel)).assertDoesNotExist()
    }

    @Test
    fun placeholderGoToChannelOpensChannel() {
        val backStack = showShellWithStack()
        compose.runOnIdle {
            backStack.add(PlayerRoute(Platform.KICK, "streamer1", "Test stream", thumbnailUrl = null))
        }
        compose.onNodeWithText("Test stream").assertIsDisplayed()

        compose.onNodeWithText(string(R.string.player_go_to_channel)).performClick()

        compose.onNodeWithText(string(R.string.placeholder_channel)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(ChannelRoute(Platform.KICK, "streamer1"), backStack.last()) }
    }

    @Test
    fun startsOnFollowingWhenSignedIn() {
        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }

        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithTag(FOLLOWING_LIST).assertIsDisplayed()
    }

    @Test
    fun backAtAnotherTabsRootReturnsToTheStartTab() {
        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }
        compose.setContent { LurkTheme { LurkApp() } }
        tab(R.string.tab_search).performClick()
        compose.onNodeWithText(string(R.string.placeholder_search)).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(FOLLOWING_LIST).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.placeholder_search)).assertDoesNotExist()
    }

    @Test
    fun playerHidesTheNavigationSuite() {
        val backStack = showShellWithStack()
        tab(R.string.tab_search).assertIsDisplayed()

        compose.runOnIdle {
            backStack.add(PlayerRoute(Platform.TWITCH, "streamer1", "Test stream", thumbnailUrl = null))
        }

        compose.onNodeWithText("Test stream").assertIsDisplayed()
        TopLevel.entries.forEach { tab(it.label).assertDoesNotExist() }
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun topBarBackArrowPopsThePushedRoute() {
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.onNodeWithText(string(R.string.placeholder_channel)).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        compose.onNodeWithText(string(R.string.placeholder_browse)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), backStack.toList()) }
    }

    private companion object {
        const val STAND_IN_COOKIE = "stand-in-cookie"
        const val FOLLOWING_LIST = "following_list"
    }
}

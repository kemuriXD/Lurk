package io.github.kemurixd.lurk

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.navigation.BrowseRoute
import io.github.kemurixd.lurk.navigation.ChannelRoute
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
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
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

// A plain Application keeps LurkApplication's real Koin graph and startup work out of the test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LurkAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun startKoinWithFakes() {
        startKoin {
            modules(
                module {
                    single {
                        SessionRepository(
                            InMemorySecureStore(),
                            listOf(FakeProfileFetcher(Platform.TWITCH), FakeProfileFetcher(Platform.KICK)),
                            scope,
                        )
                    }
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
        compose.onNodeWithText(string(R.string.placeholder_following)).assertIsDisplayed()

        tab(R.string.tab_search).performClick()
        compose.onNodeWithText(string(R.string.placeholder_search)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.placeholder_following)).assertDoesNotExist()
    }

    @Test
    fun accountButtonOpensSettings() {
        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithContentDescription("Account and settings").performClick()

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
}

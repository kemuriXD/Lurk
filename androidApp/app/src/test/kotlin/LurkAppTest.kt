package io.github.kemurixd.lurk

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.accountsModule
import io.github.kemurixd.lurk.feature.browse.browseModule
import io.github.kemurixd.lurk.feature.channel.channelModule
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.following.followingModule
import io.github.kemurixd.lurk.feature.search.searchModule
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.inMemorySettingsStore
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import io.github.kemurixd.lurk.featureui.browse.BrowseTestTags
import io.github.kemurixd.lurk.featureui.channel.ChannelTestTags
import io.github.kemurixd.lurk.featureui.following.FollowingTestTags
import io.github.kemurixd.lurk.featureui.search.SearchTestTags
import io.github.kemurixd.lurk.featureui.settings.SettingsTestTags
import io.github.kemurixd.lurk.login.CookieJar
import io.github.kemurixd.lurk.login.LoginTestTags
import io.github.kemurixd.lurk.navigation.BrowseRoute
import io.github.kemurixd.lurk.navigation.CategoryRoute
import io.github.kemurixd.lurk.navigation.ChannelRoute
import io.github.kemurixd.lurk.navigation.FollowingRoute
import io.github.kemurixd.lurk.navigation.LoginRoute
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.navigation.SettingsRoute
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
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.kemurixd.lurk.designsystem.R as DesignR

/** Cookies are never there: the login page stays open until the test acts. */
private object EmptyCookieJar : CookieJar {
    override fun read(url: String): String? = null

    override suspend fun clearAll() = Unit
}

// A plain Application keeps LurkApplication's real Koin graph and startup work out of the test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LurkAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val twitch = FakeStreamingPlatform(Platform.TWITCH)

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
                    single<CookieJar> { EmptyCookieJar }
                    single<CoroutineScope>(named("appScope")) { scope }
                    single { inMemorySettingsStore() }
                    single<DispatcherProvider> { TestDispatchers(Dispatchers.Unconfined) }
                    single { sessions }
                    single { AccountsState(get()) }
                    single<List<StreamingPlatform>> { listOf(twitch, FakeStreamingPlatform(Platform.KICK)) }
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
        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()

        tab(R.string.tab_following).performClick()
        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()

        tab(R.string.tab_search).performClick()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
        compose.onNodeWithTag(FollowingTestTags.LIST).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun search(text: String) {
        compose.onNodeWithTag(SearchTestTags.FIELD).performTextInput(text)
        compose.waitUntilAtLeastOneExists(hasText("Pixelotter") or hasText("Retro Racers"), timeoutMillis = 5_000)
    }

    @Test
    fun searchResultOpensTheChannel() {
        twitch.search =
            {
                Outcome.Success(
                    SearchResults(
                        listOf(testChannel(Platform.TWITCH, "pixelotter", displayName = "Pixelotter")),
                        emptyList(),
                    ),
                )
            }
        val backStack = showShellWithStack()
        tab(R.string.tab_search).performClick()

        search("pix")
        compose.onNodeWithText("Pixelotter").performClick()

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.runOnIdle { assertEquals(ChannelRoute(Platform.TWITCH, "pixelotter"), backStack.last()) }
    }

    @Test
    fun searchCategoryOpensTheCategoryPage() {
        twitch.search = {
            Outcome.Success(SearchResults(emptyList(), listOf(Category(Platform.TWITCH, "42", "Retro Racers", null))))
        }
        val backStack = showShellWithStack()
        tab(R.string.tab_search).performClick()

        search("retro")
        compose.onNodeWithText("Retro Racers").performClick()

        compose.runOnIdle { assertEquals(CategoryRoute(Platform.TWITCH, "42", "Retro Racers"), backStack.last()) }
    }

    @Test
    fun accountButtonOpensSettings() {
        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).performClick()

        compose.onNodeWithTag(SettingsTestTags.ACCOUNTS).assertIsDisplayed()
    }

    @Test
    fun settingsSignInOpensTheLoginAndCancelReturns() {
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(SettingsRoute) }

        compose.onNodeWithTag(SettingsTestTags.action(Platform.KICK)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { backStack.last() == LoginRoute(Platform.KICK) }
        compose.onNodeWithTag(LoginTestTags.CANCEL).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { backStack.last() == SettingsRoute }
        compose.onNodeWithTag(SettingsTestTags.ACCOUNTS).assertIsDisplayed()
    }

    @Test
    fun loginHidesTheNavigationSuite() {
        val backStack = showShellWithStack()
        tab(R.string.tab_search).assertIsDisplayed()

        compose.runOnIdle { backStack.add(LoginRoute(Platform.TWITCH)) }

        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        TopLevel.entries.forEach { tab(it.label).assertDoesNotExist() }
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun followingSignInOpensTheLoginAndBackReturns() {
        lateinit var backStack: NavBackStack<NavKey>
        compose.setContent {
            backStack = rememberNavBackStack(FollowingRoute)
            LurkTheme { LurkApp(backStack) }
        }
        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()

        compose.onNode(hasText("Sign in to Twitch") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntil(timeoutMillis = 5_000) { backStack.last() == FollowingRoute }
        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun backFromChannelReturnsToOrigin() {
        val backStack = showShellWithStack()
        tab(R.string.tab_search).performClick()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
        compose.onNodeWithTag(ChannelTestTags.BACK).assertDoesNotExist()
    }

    @Test
    fun placeholderGoToChannelOpensChannel() {
        val backStack = showShellWithStack()
        compose.runOnIdle {
            backStack.add(PlayerRoute(Platform.KICK, "streamer1", "Test stream", thumbnailUrl = null))
        }
        compose.onNodeWithText("Test stream").assertIsDisplayed()

        compose.onNodeWithText(string(R.string.player_go_to_channel)).performClick()

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.runOnIdle { assertEquals(ChannelRoute(Platform.KICK, "streamer1"), backStack.last()) }
    }

    @Test
    fun startsOnFollowingWhenSignedIn() {
        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }

        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun backAtAnotherTabsRootReturnsToTheStartTab() {
        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }
        compose.setContent { LurkTheme { LurkApp() } }
        tab(R.string.tab_search).performClick()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertDoesNotExist()
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
        compose.runOnIdle { backStack.add(CategoryRoute(Platform.TWITCH, "42", "Retro Racers")) }
        compose.onNodeWithTag(BrowseTestTags.CATEGORY_LIST).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), backStack.toList()) }
    }

    @Test
    fun channelDrawsItsOwnHeaderInsteadOfTheTopBar() {
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun channelBackButtonPopsTheRoute() {
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.BACK).performClick()

        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), backStack.toList()) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun channelLivePreviewPushesThePlayer() {
        val stream = testStream(Platform.TWITCH, login = "streamer1", title = "Speedrun night")
        twitch.channel = { Outcome.Success(testChannel(Platform.TWITCH, live = stream)) }
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.waitUntilAtLeastOneExists(hasTestTag(ChannelTestTags.LIVE_PREVIEW), timeoutMillis = 5_000)

        compose.onNodeWithTag(ChannelTestTags.LIVE_PREVIEW).performClick()

        compose.waitUntilAtLeastOneExists(hasText("Speedrun night"), timeoutMillis = 5_000)
        compose.runOnIdle {
            assertEquals(
                PlayerRoute(Platform.TWITCH, "streamer1", "Speedrun night", thumbnailUrl = null),
                backStack.last(),
            )
        }
    }

    @Test
    fun channelSignInButtonOpensTheLoginRoute() {
        val backStack = showShellWithStack()
        compose.runOnIdle { backStack.add(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.SIGN_IN).performClick()

        compose.runOnIdle { assertEquals(LoginRoute(Platform.TWITCH), backStack.last()) }
    }

    private companion object {
        const val STAND_IN_COOKIE = "stand-in-cookie"
    }
}

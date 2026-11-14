package io.github.kemurixd.lurk

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.designsystem.RELEASES_URL
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
import io.github.kemurixd.lurk.navigation.Navigator
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.navigation.SearchRoute
import io.github.kemurixd.lurk.navigation.SettingsRoute
import io.github.kemurixd.lurk.navigation.TopLevel
import io.github.kemurixd.lurk.navigation.rememberNavigator
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
import org.robolectric.Shadows.shadowOf
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
                    single<CookieJar> { EmptyCookieJar }
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

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = compose.activity.getString(id, *args)

    private fun tab(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    /** Shows the shell over a navigator the test can push onto, starting on [start] (Browse by default). */
    private fun showShell(start: TopLevel = TopLevel.BROWSE): Navigator {
        lateinit var navigator: Navigator
        compose.setContent {
            navigator = rememberNavigator(start)
            CompositionLocalProvider(LocalUriHandler provides uris) { LurkTheme { LurkApp(navigator) } }
        }
        return navigator
    }

    private val opened = mutableListOf<String>()

    private val uris =
        object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }

    @OptIn(ExperimentalTestApi::class)
    private fun clickErrorAction(label: String) {
        compose.waitUntilAtLeastOneExists(hasText(label) and hasClickAction(), timeoutMillis = 5_000)
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    @Test
    fun expiredSessionSignInAgainOpensThatPlatformsLogin() {
        twitch.topLive = { Outcome.Failure(LurkError.AuthExpired) }
        val navigator = showShell()

        clickErrorAction("Sign in again")

        compose.runOnIdle { assertEquals(LoginRoute(Platform.TWITCH), navigator.currentStack.last()) }
    }

    @Test
    // A phone wide enough for row cards, so the footer under the first page is composed without scrolling.
    @Config(qualifiers = "w360dp-h780dp")
    fun signedOutLoadMoreSignInOpensThatPlatformsLogin() {
        kick.topLive = { cursor ->
            if (cursor == null) {
                Outcome.Success(Page(listOf(testStream(Platform.KICK, login = "streamer2")), "next"))
            } else {
                Outcome.Failure(LurkError.NotSignedIn)
            }
        }
        val navigator = showShell()

        clickErrorAction("Sign in")

        compose.runOnIdle { assertEquals(LoginRoute(Platform.KICK), navigator.currentStack.last()) }
    }

    @Test
    fun proxyFailureOpensSettings() {
        twitch.topLive = { Outcome.Failure(LurkError.ProxyFailed) }
        val navigator = showShell()

        clickErrorAction("Open proxy settings")

        compose.runOnIdle { assertEquals(SettingsRoute, navigator.currentStack.last()) }
    }

    @Test
    fun schemaChangeOpensTheReleasesPage() {
        twitch.topLive = { Outcome.Failure(LurkError.SchemaChanged) }
        showShell()

        clickErrorAction("Check for update")

        compose.runOnIdle { assertEquals(listOf(RELEASES_URL), opened) }
    }

    @Test
    fun missingChannelGoesBack() {
        twitch.channel = { Outcome.Failure(LurkError.NotFound) }
        val navigator = showShell()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }

        clickErrorAction("Go back")

        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack.toList()) }
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
        val navigator = showShell()
        tab(R.string.tab_search).performClick()

        search("pix")
        compose.onNodeWithText("Pixelotter").performClick()

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.runOnIdle { assertEquals(ChannelRoute(Platform.TWITCH, "pixelotter"), navigator.currentStack.last()) }
    }

    @Test
    fun searchCategoryOpensTheCategoryPage() {
        twitch.search = {
            Outcome.Success(SearchResults(emptyList(), listOf(Category(Platform.TWITCH, "42", "Retro Racers", null))))
        }
        val navigator = showShell()
        tab(R.string.tab_search).performClick()

        search("retro")
        compose.onNodeWithText("Retro Racers").performClick()

        compose.runOnIdle {
            assertEquals(
                CategoryRoute(Platform.TWITCH, "42", "Retro Racers"),
                navigator.currentStack.last(),
            )
        }
    }

    @Test
    fun accountButtonOpensSettings() {
        compose.setContent { LurkTheme { LurkApp() } }

        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).performClick()

        compose.onNodeWithTag(SettingsTestTags.ACCOUNTS).assertIsDisplayed()
    }

    @Test
    fun settingsSignInOpensTheLoginAndCancelReturns() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(SettingsRoute) }

        compose.onNodeWithTag(SettingsTestTags.action(Platform.KICK)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { navigator.currentStack.last() == LoginRoute(Platform.KICK) }
        compose.onNodeWithTag(LoginTestTags.CANCEL).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { navigator.currentStack.last() == SettingsRoute }
        compose.onNodeWithTag(SettingsTestTags.ACCOUNTS).assertIsDisplayed()
    }

    @Test
    fun loginHidesTheNavigationSuite() {
        val navigator = showShell()
        tab(R.string.tab_search).assertIsDisplayed()

        compose.runOnIdle { navigator.push(LoginRoute(Platform.TWITCH)) }

        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        TopLevel.entries.forEach { tab(it.label).assertDoesNotExist() }
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun followingSignInOpensTheLoginAndBackReturns() {
        val navigator = showShell(TopLevel.FOLLOWING)
        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()

        compose.onNode(hasText("Sign in to Twitch") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntil(timeoutMillis = 5_000) { navigator.currentStack.last() == FollowingRoute }
        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun backFromChannelReturnsToOrigin() {
        val navigator = showShell()
        tab(R.string.tab_search).performClick()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
        compose.onNodeWithTag(ChannelTestTags.BACK).assertDoesNotExist()
    }

    @Test
    fun placeholderGoToChannelOpensChannel() {
        val navigator = showShell()
        compose.runOnIdle {
            navigator.push(PlayerRoute(Platform.KICK, "streamer1", "Test stream", thumbnailUrl = null))
        }
        compose.onNodeWithText("Test stream").assertIsDisplayed()

        compose.onNodeWithText(string(R.string.player_go_to_channel)).performClick()

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.runOnIdle { assertEquals(ChannelRoute(Platform.KICK, "streamer1"), navigator.currentStack.last()) }
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
    fun systemBackPopsCategorySettingsAndPlayerOneAtATime() {
        val navigator = showShell()
        val pages =
            listOf(
                CategoryRoute(Platform.TWITCH, "509658", "Just Chatting"),
                SettingsRoute,
                PlayerRoute(Platform.TWITCH, "streamer1", "Test stream", thumbnailUrl = null),
            )
        pages.forEach { page ->
            compose.runOnIdle { navigator.push(page) }
            compose.waitForIdle()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack.toList()) }
            compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        }
    }

    @Test
    fun playerHidesTheNavigationSuite() {
        val navigator = showShell()
        tab(R.string.tab_search).assertIsDisplayed()

        compose.runOnIdle {
            navigator.push(PlayerRoute(Platform.TWITCH, "streamer1", "Test stream", thumbnailUrl = null))
        }

        compose.onNodeWithText("Test stream").assertIsDisplayed()
        TopLevel.entries.forEach { tab(it.label).assertDoesNotExist() }
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun topBarBackArrowPopsThePushedRoute() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(CategoryRoute(Platform.TWITCH, "42", "Retro Racers")) }
        compose.onNodeWithTag(BrowseTestTags.CATEGORY_LIST).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack.toList()) }
    }

    @Test
    fun channelDrawsItsOwnHeaderInsteadOfTheTopBar() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(DesignR.string.cd_account_and_settings)).assertDoesNotExist()
    }

    @Test
    fun channelBackButtonPopsTheRoute() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.BACK).performClick()

        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf<NavKey>(BrowseRoute), navigator.currentStack.toList()) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun channelLivePreviewPushesThePlayer() {
        val stream = testStream(Platform.TWITCH, login = "streamer1", title = "Speedrun night")
        twitch.channel = { Outcome.Success(testChannel(Platform.TWITCH, live = stream)) }
        val navigator = showShell()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.waitUntilAtLeastOneExists(hasTestTag(ChannelTestTags.LIVE_PREVIEW), timeoutMillis = 5_000)

        compose.onNodeWithTag(ChannelTestTags.LIVE_PREVIEW).performClick()

        compose.waitUntilAtLeastOneExists(hasText("Speedrun night"), timeoutMillis = 5_000)
        compose.runOnIdle {
            assertEquals(
                PlayerRoute(Platform.TWITCH, "streamer1", "Speedrun night", thumbnailUrl = null),
                navigator.currentStack.last(),
            )
        }
    }

    @Test
    fun channelSignInButtonOpensTheLoginRoute() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }

        compose.onNodeWithTag(ChannelTestTags.SIGN_IN).performClick()

        compose.runOnIdle { assertEquals(LoginRoute(Platform.TWITCH), navigator.currentStack.last()) }
    }

    @Test
    fun switchingAwayAndBackShowsTheSamePage() {
        val navigator = showShell()
        compose.runOnIdle { navigator.push(CategoryRoute(Platform.TWITCH, "42", "Retro Racers")) }
        compose.onNodeWithTag(BrowseTestTags.CATEGORY_LIST).assertIsDisplayed()

        tab(R.string.tab_search).performClick()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()
        tab(R.string.tab_browse).performClick()

        compose.onNodeWithTag(BrowseTestTags.CATEGORY_LIST).assertIsDisplayed()
    }

    @Test
    fun backAtAnotherTabsRootShowsTheStartTabAsItWasLeft() {
        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }
        val navigator = showShell(TopLevel.FOLLOWING)
        compose.runOnIdle { navigator.push(ChannelRoute(Platform.TWITCH, "streamer1")) }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        tab(R.string.tab_search).performClick()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.onNodeWithTag(SearchTestTags.FIELD).assertDoesNotExist()
    }

    @Test
    fun aHiddenTabKeepsItsState() {
        showShell()
        tab(R.string.tab_search).performClick()
        compose.onNodeWithTag(SearchTestTags.FIELD).performTextInput("pix")

        tab(R.string.tab_browse).performClick()
        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        tab(R.string.tab_search).performClick()

        compose.onNodeWithTag(SearchTestTags.FIELD).assert(hasText("pix"))
    }

    @Test
    fun theSameChannelOpenInTwoTabs() {
        val navigator = showShell()
        val channel = ChannelRoute(Platform.TWITCH, "streamer1")
        compose.runOnIdle { navigator.push(channel) }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        tab(R.string.tab_search).performClick()
        compose.runOnIdle { navigator.push(channel) }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()

        tab(R.string.tab_browse).performClick()
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
        compose.onNodeWithTag(ChannelTestTags.BACK).performClick()

        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
    }

    @Test
    fun signingInMakesFollowingTheStartTabWithoutMovingThePage() {
        compose.setContent { LurkTheme { LurkApp() } }
        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()

        runBlocking { sessions.signIn(Platform.TWITCH, STAND_IN_COOKIE) }
        compose.waitForIdle()
        compose.onNodeWithTag(BrowseTestTags.TOP_LIVE_LIST).assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag(FollowingTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun stacksAndTheTabInFrontSurviveRecreation() {
        val restore = StateRestorationTester(compose)
        lateinit var navigator: Navigator
        restore.setContent {
            navigator = rememberNavigator(TopLevel.BROWSE)
            LurkTheme { LurkApp(navigator) }
        }
        val category = CategoryRoute(Platform.TWITCH, "42", "Retro Racers")
        val channel = ChannelRoute(Platform.TWITCH, "streamer1")
        compose.runOnIdle { navigator.push(category) }
        tab(R.string.tab_search).performClick()
        compose.runOnIdle { navigator.push(channel) }

        restore.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertEquals(TopLevel.SEARCH, navigator.current)
            assertEquals(TopLevel.BROWSE, navigator.start)
            assertEquals(listOf(SearchRoute, channel), navigator.currentStack.toList())
            assertEquals(listOf(BrowseRoute, category), navigator.stack(TopLevel.BROWSE).toList())
        }
        compose.onNodeWithTag(ChannelTestTags.BACK).assertIsDisplayed()
    }

    private fun webViewIn(view: View): WebView? =
        when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { webViewIn(view.getChildAt(it)) }
            else -> null
        }

    @Test
    fun afterRecreationBackOnTheLoginStillGoesBackInThePageFirst() {
        val restore = StateRestorationTester(compose)
        lateinit var navigator: Navigator
        restore.setContent {
            navigator = rememberNavigator(TopLevel.BROWSE)
            LurkTheme { LurkApp(navigator) }
        }
        compose.runOnIdle { navigator.push(LoginRoute(Platform.TWITCH)) }
        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()

        // The process comes back with the login on top: its back handler is composed before the shell's, and both are
        // added to the dispatcher when the activity starts.
        restore.emulateSavedInstanceStateRestore()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        val page = compose.runOnIdle { checkNotNull(webViewIn(compose.activity.window.decorView)) }
        compose.runOnIdle { shadowOf(page).setCanGoBack(true) }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(1, shadowOf(page).goBackInvocations)
            assertEquals(LoginRoute(Platform.TWITCH), navigator.currentStack.last())
        }
        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
    }

    private companion object {
        const val STAND_IN_COOKIE = "stand-in-cookie"
    }
}

package io.github.kemurixd.lurk.login

import android.app.Application
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme
import io.github.kemurixd.lurk.feature.accounts.LoginViewModel
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.github.kemurixd.lurk.designsystem.R as DesignR

/** A [CookieJar] in memory. [gate], when set, holds every [clearAll] until it completes. */
private class FakeCookieJar(
    private val events: MutableList<String>,
) : CookieJar {
    var header: String? = null
    var gate: CompletableDeferred<Unit>? = null
    val reads = mutableListOf<String>()

    override fun read(url: String): String? {
        reads += url
        return header
    }

    override suspend fun clearAll() {
        events += "clear started"
        gate?.await()
        header = null
        events += "clear done"
    }
}

// A plain Application keeps LurkApplication's real Koin graph and startup work out of the test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fetchers = Platform.entries.associateWith { FakeProfileFetcher(it) }
    private val sessions = SessionRepository(InMemorySecureStore(), fetchers.values.toList(), scope)
    private val events = mutableListOf<String>()
    private val jar = FakeCookieJar(events)
    private var done = 0
    private var cancelled = 0
    private val logged = mutableListOf<Pair<String, String>>()

    @After
    fun tearDown() {
        scope.cancel()
        Logger.setLogWriters(platformLogWriter())
    }

    private fun show(platform: Platform = Platform.TWITCH) {
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    logged += tag to message
                }
            },
        )
        val viewModel = LoginViewModel(platform, sessions, TestDispatchers(Dispatchers.Main))
        compose.setContent {
            LurkTheme(dynamicColor = false) {
                LoginScreen(
                    platform = platform,
                    onDone = { done++ },
                    onCancel = { cancelled++ },
                    viewModel = viewModel,
                    cookieJar = jar,
                    loader = { _: WebView, url: String -> events += "load $url" },
                )
            }
        }
        compose.waitForIdle()
    }

    /** Lets one poll run: the screen checks the cookie every 500 ms while it is visible. */
    private fun poll() {
        compose.mainClock.advanceTimeBy(POLL_MILLIS)
        compose.waitForIdle()
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = compose.activity.getString(id, *args)

    @Test
    fun clearsCookiesBeforeLoad() {
        val gate = CompletableDeferred<Unit>()
        jar.gate = gate

        show()
        assertEquals(listOf("clear started"), events)

        gate.complete(Unit)
        compose.waitForIdle()

        assertEquals(listOf("clear started", "clear done", "load https://www.twitch.tv/login"), events)
    }

    @Test
    fun showsCancelAndThePlatformTitle() {
        show(Platform.KICK)

        compose.onNodeWithTag(LoginTestTags.CANCEL).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.title_login, string(DesignR.string.platform_kick))).assertIsDisplayed()
    }

    @Test
    fun foundCookieSignsInAndCloses() {
        show(Platform.KICK)
        jar.header = "other=1; session_token=$COOKIE"

        poll()
        compose.waitUntil(timeoutMillis = 5_000) { done == 1 }

        assertIs<AccountState.SignedIn>(sessions.state(Platform.KICK).value)
        assertEquals(1, fetchers.getValue(Platform.KICK).calls)
        assertEquals(0, cancelled)
        assertTrue("https://kick.com" in jar.reads)
        // The cookie is cleared again after signing in, so it doesn't linger in the WebView's store.
        assertEquals(2, events.count { it == "clear done" })
    }

    @Test
    fun cookieValueNeverReachesTheLog() {
        show(Platform.TWITCH)
        poll()
        jar.header = "auth-token=$COOKIE"
        poll()
        compose.waitUntil(timeoutMillis = 5_000) { done == 1 }

        val login = logged.filter { it.first == "Login" }.map { it.second }
        assertTrue("cookie found" in login, "logged: $login")
        assertTrue(login.all { it == "cookie found" || it == "cookie not found" }, "logged: $login")
        assertFalse(logged.any { COOKIE in it.second })
        assertTrue("https://twitch.tv" in jar.reads)
    }

    @Test
    fun signInFailureKeepsLoginOpen() {
        fetchers.getValue(Platform.TWITCH).result = Outcome.Failure(LurkError.Offline)
        show()
        jar.header = "auth-token=$COOKIE"

        poll()

        compose.onNodeWithTag(LoginTestTags.ERROR).assertIsDisplayed()
        assertEquals(0, done)
        assertEquals(0, cancelled)
        assertEquals(AccountState.SignedOut, sessions.state(Platform.TWITCH).value)
        // Cleared again and the login page reloaded for another try.
        assertEquals(
            listOf(
                "clear started",
                "clear done",
                "load https://www.twitch.tv/login",
                "clear started",
                "clear done",
                "load https://www.twitch.tv/login",
            ),
            events,
        )
    }

    @Test
    fun cancelReturnsWithoutSignIn() {
        show()

        compose.onNodeWithTag(LoginTestTags.CANCEL).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { cancelled == 1 }

        assertEquals(0, done)
        assertEquals(0, fetchers.getValue(Platform.TWITCH).calls)
        assertEquals(2, events.count { it == "clear done" })
    }

    @Test
    fun systemBackCancels() {
        show()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(timeoutMillis = 5_000) { cancelled == 1 }

        assertEquals(0, done)
    }

    @Test
    fun noPollingAfterDone() {
        show()
        compose.onNodeWithTag(LoginTestTags.CANCEL).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { cancelled == 1 }
        val readsAtDone = jar.reads.size

        poll()
        poll()

        assertEquals(readsAtDone, jar.reads.size)
    }

    private companion object {
        const val COOKIE = "test-cookie"
        const val POLL_MILLIS = 600L
    }
}

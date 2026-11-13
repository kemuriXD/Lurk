package io.github.kemurixd.lurk.login

import android.app.Application
import android.webkit.WebView
import io.github.kemurixd.lurk.auth.LoginSpec
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.feature.accounts.LoginEffect
import io.github.kemurixd.lurk.feature.accounts.LoginIntent
import io.github.kemurixd.lurk.feature.accounts.LoginUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginSessionTest {
    private val events = mutableListOf<String>()
    private val jar = FakeCookieJar(events)
    private val state = MutableStateFlow<LoginUiState>(LoginUiState.Ready)
    private val intents = mutableListOf<LoginIntent>()
    private val session =
        LoginSession(LoginSpec.TWITCH, jar, { _, url -> events += "load $url" }, state, { intents += it })

    /** What the screen's WebView factory does. */
    private suspend fun startOnAWebView() {
        val view = WebView(RuntimeEnvironment.getApplication())
        session.webView = view
        session.start(view)
    }

    private fun cookiesFound(): Int = intents.count { it is LoginIntent.CookieFound }

    @Test
    fun noCookieCheckBetweenAFailureAndItsClear() =
        runBlocking {
            startOnAWebView()
            jar.header = "auth-token=$COOKIE"
            // The sign-in failed; the view model's ClearCookies has not been handled yet.
            state.value = LoginUiState.Failed(LurkError.Offline)

            session.check()
            session.onPageFinished()
            assertEquals(0, cookiesFound())

            session.handle(LoginEffect.ClearCookies, onDone = {}, onCancel = {})
            assertEquals(listOf("load $LOGIN_URL"), events.filter { it.startsWith("load") }.drop(1))

            // The user signs in again on the reloaded page.
            jar.header = "auth-token=$COOKIE"
            session.check()
            assertEquals(1, cookiesFound())
        }

    @Test
    fun aSecondFailureWaitsForItsOwnClear() =
        runBlocking {
            startOnAWebView()
            state.value = LoginUiState.Failed(LurkError.Offline)
            session.handle(LoginEffect.ClearCookies, onDone = {}, onCancel = {})

            // The retry failed the same way (the view model passes through SigningIn); its clear is still pending.
            jar.header = "auth-token=$COOKIE"
            state.value = LoginUiState.SigningIn
            state.value = LoginUiState.Failed(LurkError.Offline)
            session.check()

            assertEquals(0, cookiesFound())
        }

    @Test
    fun aSessionStartedAfterAFailureChecksAgain() =
        runBlocking {
            // Rotation: the view model kept its failure, the screen and its session are new.
            state.value = LoginUiState.Failed(LurkError.Offline)
            startOnAWebView()

            jar.header = "auth-token=$COOKIE"
            session.check()

            assertEquals(1, cookiesFound())
        }

    private companion object {
        const val COOKIE = "test-cookie"
        const val LOGIN_URL = "https://www.twitch.tv/login"
    }
}

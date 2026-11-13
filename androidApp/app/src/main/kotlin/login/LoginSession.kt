package io.github.kemurixd.lurk.login

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.auth.LoginSpec
import io.github.kemurixd.lurk.feature.accounts.LoginEffect
import io.github.kemurixd.lurk.feature.accounts.LoginIntent
import io.github.kemurixd.lurk.feature.accounts.LoginUiState
import kotlinx.coroutines.flow.StateFlow

/** Loads a URL into the login WebView. A seam, so tests can see when the first load happens. */
fun interface WebViewLoader {
    fun load(
        view: WebView,
        url: String,
    )
}

/** The stock load: no extra headers, and the WebView's own user agent. */
internal val StockWebViewLoader = WebViewLoader { view, url -> view.loadUrl(url) }

// Logs only whether the cookie is there. Never its value, its header or the page URL.
private val log = Logger.withTag("Login")

/**
 * The login screen's work around one WebView: clear, load, watch for the cookie, act on the view model's effects.
 * [state] and [onIntent] are the `LoginViewModel`'s.
 * Lives as long as the screen's composition and is used only on the main thread.
 */
internal class LoginSession(
    private val spec: LoginSpec,
    private val jar: CookieJar,
    private val loader: WebViewLoader,
    private val state: StateFlow<LoginUiState>,
    private val onIntent: (LoginIntent) -> Unit,
) {
    private val cookieUrl = "https://" + spec.cookieDomain.trimStart('.')

    var webView: WebView? = null

    /** True once the login page has been asked for, after the first clear. Checks before that would be pointless. */
    var loaded by mutableStateOf(false)
        private set

    /** True after [LoginEffect.Done]: no more checks, and the screen is on its way out. */
    var finished by mutableStateOf(false)
        private set

    private var clearing = false
    private var cancelled = false

    /**
     * The failure whose clear and reload have finished. Until then the old cookie may still be in the store, so checks
     * wait. The view model passes through SigningIn before each failure, so every failure is a new instance.
     */
    private var settledFailure: LoginUiState.Failed? = null

    /** Clears every cookie, then loads the login page, so a previous account's cookie is never picked up. */
    suspend fun start(view: WebView) {
        clear()
        if (finished) return
        loader.load(view, spec.loginUrl)
        loaded = true
    }

    fun onPageFinished() {
        if (!loaded) return
        onIntent(LoginIntent.PageLoaded)
        check(logMiss = true)
    }

    /**
     * Looks for the session cookie. Skipped while a clear runs, while a sign-in is checking a cookie, and after a
     * failure until its clear and reload are done.
     */
    fun check(logMiss: Boolean = false) {
        if (!canCheck()) return
        val value = cookieValue(jar.read(cookieUrl), spec.cookieName)
        if (value != null) {
            log.i { "cookie found" }
            onIntent(LoginIntent.CookieFound(value))
        } else if (logMiss) {
            log.d { "cookie not found" }
        }
    }

    private fun canCheck(): Boolean {
        val current = state.value
        val waiting =
            current == LoginUiState.SigningIn || (current is LoginUiState.Failed && current !== settledFailure)
        return loaded && !clearing && !finished && !waiting
    }

    fun cancel() {
        cancelled = true
        onIntent(LoginIntent.Cancel)
    }

    suspend fun handle(
        effect: LoginEffect,
        onDone: () -> Unit,
        onCancel: () -> Unit,
    ) {
        when (effect) {
            LoginEffect.ClearCookies -> {
                val failure = state.value as? LoginUiState.Failed
                clear()
                // A failed sign-in left the user signed out on the page: load the login again for another try.
                val view = webView
                if (failure != null && view != null && !cancelled) {
                    loader.load(view, spec.loginUrl)
                    settledFailure = failure
                }
            }

            LoginEffect.Done -> {
                finished = true
                if (cancelled) onCancel() else onDone()
            }
        }
    }

    private suspend fun clear() {
        clearing = true
        try {
            jar.clearAll()
        } finally {
            clearing = false
        }
    }
}

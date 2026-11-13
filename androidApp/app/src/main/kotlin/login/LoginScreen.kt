package io.github.kemurixd.lurk.login

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.auth.LoginSpec
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.component.PlatformBadge
import io.github.kemurixd.lurk.designsystem.messageRes
import io.github.kemurixd.lurk.feature.accounts.LoginEffect
import io.github.kemurixd.lurk.feature.accounts.LoginUiState
import io.github.kemurixd.lurk.feature.accounts.LoginViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import io.github.kemurixd.lurk.designsystem.R as DesignR

private val PollInterval = 500.milliseconds

/**
 * The platform's own web login, full screen with a Cancel bar (Figma 10 Login & Account, WebView login).
 *
 * Every cookie is cleared before the login page loads. The session cookie is then looked for on every finished page
 * and every 500 ms while the screen is visible; once found, [LoginViewModel] signs in with it. Success clears the
 * cookies again and calls [onDone]. A failure shows the error, clears the cookies and reloads the page for another
 * try. Cancel, or back with no page to go back to, clears the cookies and calls [onCancel].
 */
@Composable
fun LoginScreen(
    platform: Platform,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel { parametersOf(platform) },
    cookieJar: CookieJar = koinInject(),
    loader: WebViewLoader = StockWebViewLoader,
) {
    val session =
        remember(viewModel, cookieJar, loader) {
            LoginSession(LoginSpec.forPlatform(platform), cookieJar, loader, viewModel.state, viewModel::onIntent)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RunLoginSession(session, viewModel.effects, onDone, onCancel)
    BackHandler {
        val view = session.webView
        if (view != null && view.canGoBack()) view.goBack() else session.cancel()
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LoginBar(platform, onCancel = session::cancel)
        val current = state
        if (current is LoginUiState.Failed) SignInError(current.error)
        if (current == LoginUiState.Loading || current == LoginUiState.SigningIn) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(LoginTestTags.PROGRESS))
        }
        LoginWebView(session, Modifier.weight(1f).fillMaxWidth().imePadding())
    }
}

/** Acts on the view model's effects, and polls for the cookie, while the screen is started. */
@Composable
private fun RunLoginSession(
    session: LoginSession,
    effects: Flow<LoginEffect>,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val done by rememberUpdatedState(onDone)
    val cancel by rememberUpdatedState(onCancel)
    LaunchedEffect(session, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { session.handle(it, onDone = done, onCancel = cancel) }
        }
    }
    LaunchedEffect(session, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!session.finished) {
                delay(PollInterval)
                session.check()
            }
        }
    }
}

@Composable
private fun LoginBar(
    platform: Platform,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.testTag(LoginTestTags.CANCEL)) {
            Text(stringResource(R.string.login_cancel))
        }
        Text(
            text = stringResource(R.string.title_login, stringResource(platform.label)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        PlatformBadge(platform)
    }
}

@Composable
private fun SignInError(error: LurkError) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().testTag(LoginTestTags.ERROR),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.login_failed), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The WebView. It is created once, cleared and loaded by [LoginSession.start], and destroyed when it leaves. */
@Composable
private fun LoginWebView(
    session: LoginSession,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(modifier) {
        AndroidView(
            factory = { context ->
                loginWebView(context, onPageFinished = session::onPageFinished).also { view ->
                    session.webView = view
                    scope.launch { session.start(view) }
                }
            },
            onRelease = { view ->
                session.webView = null
                view.stopLoading()
                view.webViewClient = WebViewClient()
                // Drops cached pages, which may hold account details, along with the view.
                view.clearCache(true)
                view.clearHistory()
                view.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * A WebView with only what the login pages need: JavaScript and DOM storage. No file or content access, no
 * JavaScript interface, no pop-up windows, no geolocation, and the stock user agent. JavaScript is on because the
 * login pages are JavaScript apps; nothing of the app is exposed to their scripts.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun loginWebView(
    context: Context,
    onPageFinished: () -> Unit,
): WebView =
    WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) {
                    onPageFinished()
                }
            }
    }

internal val Platform.label: Int
    get() =
        when (this) {
            Platform.TWITCH -> DesignR.string.platform_twitch
            Platform.KICK -> DesignR.string.platform_kick
        }

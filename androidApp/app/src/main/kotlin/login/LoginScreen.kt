package io.github.kemurixd.lurk.login

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.ErrorAction
import io.github.kemurixd.lurk.designsystem.component.ErrorBanner
import io.github.kemurixd.lurk.designsystem.component.PlatformBadge
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.feature.accounts.LoginEffect
import io.github.kemurixd.lurk.feature.accounts.LoginUiState
import io.github.kemurixd.lurk.feature.accounts.LoginViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.time.Duration.Companion.milliseconds

private val PollInterval = 500.milliseconds

/**
 * The platform's own web login, full screen with a Cancel bar (Figma 10 Login & Account, WebView login).
 *
 * Every cookie is cleared before the login page loads. The session cookie is then looked for on every finished page
 * and every 500 ms while the screen is visible; once found, [LoginViewModel] signs in with it. Success clears the
 * cookies again and calls [onDone]. A failure shows the error in a banner, clears the cookies and reloads the page
 * for another try; the banner's Retry does that clear and reload again and hides the banner. Cancel, or back with no
 * page to go back to, clears the cookies and calls [onCancel]. Leaving any other way clears them on [cleanupScope],
 * which outlives the screen; the app also clears them at every start.
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
    cleanupScope: CoroutineScope = koinInject(named("appScope")),
) {
    val session =
        remember(viewModel, cookieJar, loader) {
            LoginSession(LoginSpec.forPlatform(platform), cookieJar, loader, viewModel.state, viewModel::onIntent)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RunLoginSession(session, viewModel.effects, onDone, onCancel)
    // Leaving without Done (the activity finishing, the task swiped away) would leave the session cookie in the store.
    DisposableEffect(session) {
        onDispose { if (!session.finished) cleanupScope.launch { cookieJar.clearAll() } }
    }
    BackHandler {
        val view = session.webView
        if (view != null && view.canGoBack()) view.goBack() else session.cancel()
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LoginBar(platform, onCancel = session::cancel)
        val current = state
        if (current is LoginUiState.Failed) SignInError(current, session)
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.testTag(LoginTestTags.CANCEL)) {
            Text(stringResource(R.string.login_cancel))
        }
        Text(
            text = stringResource(R.string.title_login, stringResource(platform.labelRes)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        PlatformBadge(platform)
    }
}

/** The failure as an error banner. Retry clears and reloads the login page, and hides the banner until the next one. */
@Composable
private fun SignInError(
    failure: LoginUiState.Failed,
    session: LoginSession,
) {
    var retried by remember(failure) { mutableStateOf(false) }
    if (retried) return
    val scope = rememberCoroutineScope()
    Box(Modifier.testTag(LoginTestTags.ERROR)) {
        ErrorBanner(
            title = stringResource(R.string.login_failed),
            error = failure.error,
            onRetry = {
                retried = true
                scope.launch { session.retry() }
            },
            action = ErrorAction.Retry(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
internal fun loginWebView(
    context: Context,
    onPageFinished: () -> Unit,
): WebView =
    WebView(context).apply {
        // Without this AndroidView lays it out as wrap-content: the page's html element comes out 0 tall, and pages
        // sized to 100% height paint nothing.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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

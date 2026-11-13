package io.github.kemurixd.lurk.designsystem.component

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.ErrorAction
import io.github.kemurixd.lurk.designsystem.action
import io.github.kemurixd.lurk.designsystem.labelRes
import io.github.kemurixd.lurk.designsystem.messageRes

/**
 * What the error actions that leave the screen do. Only the app knows navigation, so it builds one of these over its
 * navigator and provides it once through [ErrorHost]. Retry is the screen's own, so it is passed to each surface.
 */
@Immutable
class ErrorHandlers(
    val signIn: (Platform) -> Unit = {},
    val openProxySettings: () -> Unit = {},
    val checkForUpdate: () -> Unit = {},
    val goBack: () -> Unit = {},
) {
    /** Runs [action]. Sign in needs the failed [platform]; without one it does nothing. */
    fun perform(
        action: ErrorAction,
        platform: Platform?,
        onRetry: () -> Unit,
    ) {
        when (action) {
            is ErrorAction.Retry -> onRetry()
            is ErrorAction.SignIn -> platform?.let(signIn)
            ErrorAction.OpenProxySettings -> openProxySettings()
            ErrorAction.CheckForUpdate -> checkForUpdate()
            ErrorAction.GoBack -> goBack()
        }
    }
}

/** The handlers the error surfaces use. [ErrorHost] provides the app's. */
val LocalErrorHandlers = staticCompositionLocalOf { ErrorHandlers() }

/** The one snackbar host, above the navigation bar. Null outside an [ErrorHost], where snackbars are dropped. */
private val LocalErrorSnackbarHost = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * Hosts error handling once for the whole app: provides [handlers] and the snackbar host to [content], and shows the
 * snackbar at its bottom edge (Figma Snackbar: inverseSurface, radius xs, the action in inversePrimary). The app puts
 * it inside the navigation suite, so the snackbar sits above the bar. [snackbarModifier] places the snackbar itself.
 */
@Composable
fun ErrorHost(
    handlers: ErrorHandlers,
    modifier: Modifier = Modifier,
    snackbarModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    Box(modifier) {
        CompositionLocalProvider(LocalErrorHandlers provides handlers, LocalErrorSnackbarHost provides snackbar) {
            content()
        }
        SnackbarHost(
            hostState = snackbar,
            modifier =
                snackbarModifier
                    .align(Alignment.BottomCenter)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        ) { data -> Snackbar(data, Modifier.padding(8.dp)) }
    }
}

/** Shows failures of things the user did (follow, share) as a snackbar with the mapped action. */
@Stable
class ErrorSnackbar internal constructor(
    private val host: SnackbarHostState?,
    private val resources: Resources,
    private val handlers: ErrorHandlers,
) {
    /**
     * Shows the reason for [error] with [action]'s label, replacing any snackbar already up, and runs the action if it
     * is tapped. [platform] is the one to sign in to; [onRetry] is what Retry asks again.
     */
    suspend fun showError(
        error: LurkError,
        action: ErrorAction? = error.action(),
        platform: Platform? = null,
        onRetry: () -> Unit = {},
    ) {
        val host = host ?: return
        host.currentSnackbarData?.dismiss()
        val label = action?.let { resources.getString(it.labelRes()) }
        val result =
            host.showSnackbar(
                message = resources.getString(error.messageRes()),
                actionLabel = label,
                duration = if (label == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed && action != null) handlers.perform(action, platform, onRetry)
    }
}

/** The app's [ErrorSnackbar], from the nearest [ErrorHost]. */
@Composable
fun rememberErrorSnackbar(): ErrorSnackbar {
    val host = LocalErrorSnackbarHost.current
    val resources = LocalResources.current
    val handlers = LocalErrorHandlers.current
    return remember(host, resources, handlers) { ErrorSnackbar(host, resources, handlers) }
}

package io.github.kemurixd.lurk

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.designsystem.RELEASES_URL
import io.github.kemurixd.lurk.designsystem.component.ErrorHandlers
import io.github.kemurixd.lurk.designsystem.component.ErrorHost
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.navigation.LoginRoute
import io.github.kemurixd.lurk.navigation.LurkNavHost
import io.github.kemurixd.lurk.navigation.Navigator
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.navigation.TopLevel
import io.github.kemurixd.lurk.navigation.rememberNavigator
import io.github.kemurixd.lurk.navigation.startDestination
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * The app shell. The start tab follows sign-in: Following when signed in, Browse when not. A sign-in or sign-out
 * changes it without moving the page in front (see [Navigator.changeStart]).
 */
@Composable
fun LurkApp(modifier: Modifier = Modifier) {
    val accounts = koinInject<AccountsState>()
    val navigator = rememberNavigator(remember { startDestination(accounts.states.value) })
    val states by accounts.states.collectAsState()
    val start = startDestination(states)
    LaunchedEffect(navigator, start) { navigator.changeStart(start) }
    LurkApp(navigator, modifier)
}

/**
 * The shell over a given navigator: the navigation bar or rail around the Navigation 3 display. The player and the
 * login are full screen, so the navigation suite hides while either is on top.
 */
@Composable
fun LurkApp(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val layoutType =
        if (navigator.currentStack.lastOrNull().isFullScreen()) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
        }
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevel.entries.forEach { tab ->
                item(
                    selected = tab == navigator.current,
                    onClick = { navigator.select(tab) },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        },
    ) {
        // Pads the top and the sides (status bar, landscape, cutouts) and then marks the strip under the content, where
        // the bar sits, as consumed. A screen's own imePadding() then adds only the part of the keyboard that rises
        // above the bar. The bottom edge is not padded here: the scaffold already ends the content above the bar.
        // The one error host wraps every page, so its snackbar sits above the bar (or above the gesture area on the
        // full-screen pages, where the bar is hidden).
        val uriHandler = LocalUriHandler.current
        ErrorHost(
            handlers = remember(navigator, uriHandler) { errorHandlers(navigator, uriHandler) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ).consumeBarInsets(),
            snackbarModifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            LurkNavHost(navigator, Modifier.fillMaxSize())
        }
    }
}

/**
 * What the error surfaces' actions do, over the app's navigator: Sign in opens that platform's login, Open proxy
 * settings opens Settings, Go back pops the page, and Check for update opens the GitHub Releases page.
 */
internal fun errorHandlers(
    navigator: Navigator,
    uriHandler: UriHandler,
): ErrorHandlers =
    ErrorHandlers(
        signIn = { platform -> navigator.push(LoginRoute(platform)) },
        openProxySettings = navigator::openSettings,
        checkForUpdate = { uriHandler.openUri(RELEASES_URL) },
        goBack = navigator::back,
    )

/** The player and the login draw edge to edge with their own controls, so the navigation suite hides under them. */
private fun NavKey?.isFullScreen(): Boolean = this is PlayerRoute || this is LoginRoute

/**
 * Marks the space between this node's bottom edge and the window's bottom edge as consumed insets. That is where the
 * navigation bar and the gesture area sit, already clear of the content. With nothing below (the player, or beside a
 * rail) the gap is zero and nothing changes. Measured after layout, so it follows the bar's real height.
 */
@Composable
internal fun Modifier.consumeBarInsets(): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var gapPx by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { gapPx.toDp() }
    return onGloballyPositioned { coordinates ->
        gapPx =
            (windowHeight - coordinates.positionInWindow().y - coordinates.size.height).roundToInt().coerceAtLeast(0)
    }.consumeWindowInsets(PaddingValues(bottom = gap))
}

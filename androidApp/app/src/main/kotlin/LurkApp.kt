package io.github.kemurixd.lurk

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.navigation.LurkNavHost
import io.github.kemurixd.lurk.navigation.Navigator
import io.github.kemurixd.lurk.navigation.PlayerRoute
import io.github.kemurixd.lurk.navigation.TopLevel
import io.github.kemurixd.lurk.navigation.startDestination
import org.koin.compose.koinInject

/**
 * The app shell. The start tab is read once, from the sign-in state the splash waited for. After that the saved back
 * stack decides what shows, so signing in or out later doesn't move the user.
 */
@Composable
fun LurkApp(modifier: Modifier = Modifier) {
    val accounts = koinInject<AccountsState>()
    val start = remember { startDestination(accounts.states.value) }
    val backStack = rememberNavBackStack(start.route)
    LurkApp(backStack, modifier)
}

/**
 * The shell over a given back stack: the navigation bar or rail around the Navigation 3 display. The stack's first key
 * is the start tab's root. The player is full screen, so the navigation suite hides while it is on top.
 */
@Composable
fun LurkApp(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val navigator =
        remember(backStack) { Navigator(backStack, start = TopLevel.of(backStack.firstOrNull()) ?: TopLevel.BROWSE) }
    val current = TopLevel.current(backStack)
    val layoutType =
        if (backStack.lastOrNull() is PlayerRoute) {
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
                    selected = tab == current,
                    onClick = { navigator.select(tab) },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        },
    ) {
        // The bar or rail consumes the insets it covers, so this pads only what is left: the status bar, and the
        // sides in landscape or beside a cutout.
        LurkNavHost(
            backStack,
            navigator,
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        )
    }
}

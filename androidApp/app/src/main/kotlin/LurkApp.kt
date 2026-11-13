package io.github.kemurixd.lurk

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
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

/** The shell over a given back stack: the navigation bar or rail around the Navigation 3 display. */
@Composable
fun LurkApp(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(backStack) { Navigator(backStack) }
    val current = TopLevel.of(backStack)
    NavigationSuiteScaffold(
        modifier = modifier,
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
        LurkNavHost(backStack, navigator, Modifier.statusBarsPadding())
    }
}

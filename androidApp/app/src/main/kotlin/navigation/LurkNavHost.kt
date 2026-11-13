package io.github.kemurixd.lurk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.LurkTopBar
import io.github.kemurixd.lurk.featureui.browse.BrowseEntry
import io.github.kemurixd.lurk.featureui.browse.CategoryEntry
import io.github.kemurixd.lurk.featureui.channel.ChannelEntry
import io.github.kemurixd.lurk.featureui.following.FollowingEntry
import io.github.kemurixd.lurk.featureui.search.SearchEntry
import io.github.kemurixd.lurk.featureui.settings.SettingsEntry
import io.github.kemurixd.lurk.login.LoginScreen
import io.github.kemurixd.lurk.player.PlayerPlaceholderScreen
import io.github.kemurixd.lurk.designsystem.R as DesignR

/**
 * The Navigation 3 display, showing the stack of the tab in front.
 *
 * Every tab's entries stay decorated whether shown or not, so a hidden tab keeps each page's saved state (scroll
 * position, typed text) and its ViewModels until the page is popped from that tab's own stack. The display only gets
 * the tab in front.
 */
@Composable
fun LurkNavHost(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val decorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        )
    val routes = routes(navigator)
    val entries =
        TopLevel.entries.associateWith { tab ->
            key(tab) { rememberTabEntries(tab, navigator.stack(tab), decorators, routes) }
        }
    NavDisplay(
        entries = entries.getValue(navigator.current),
        modifier = modifier,
        onBack = navigator::back,
        transitionSpec = { slide(NavigationKind.PUSH) },
        popTransitionSpec = { slide(NavigationKind.POP) },
        // Unused while the handler below pops on release; if the display ever previews a back swipe, it is the pop.
        predictivePopTransitionSpec = { slide(NavigationKind.POP) },
    )
    // A plain back callback, registered after the display's, so the system calls it on release and the page plays
    // the same pop slide as the back button. Without it the display previews the swipe, seeking the page under the
    // finger. At the start tab's root there is nothing to go back to, so the system's own back-to-home runs.
    BackHandler(enabled = navigator.canGoBack, onBack = navigator::back)
}

private fun routes(navigator: Navigator): (NavKey) -> NavEntry<NavKey> =
    entryProvider {
        entry<FollowingRoute> { TabPage(TopLevel.FOLLOWING, navigator) { FollowingTab(navigator) } }
        entry<BrowseRoute> { TabPage(TopLevel.BROWSE, navigator) { BrowseTab(navigator) } }
        entry<SearchRoute> { TabPage(TopLevel.SEARCH, navigator) { SearchTab(navigator) } }
        entry<CategoryRoute> { route ->
            PushedPage(route.name, navigator) { CategoryPage(route, navigator) }
        }
        entry<ChannelRoute> { route ->
            // The channel draws its own banner and back button (Figma), so it skips the shared top bar.
            ChannelPage(route, navigator)
        }
        entry<PlayerRoute> { route ->
            PlayerPlaceholderScreen(
                route = route,
                onGoToChannel = { navigator.push(ChannelRoute(route.platform, route.login)) },
                onBack = navigator::back,
            )
        }
        entry<SettingsRoute> {
            PushedPage(stringResource(R.string.title_settings), navigator) {
                SettingsEntry(onOpenLogin = { platform -> navigator.push(LoginRoute(platform)) })
            }
        }
        entry<LoginRoute> { route ->
            // Full screen with its own Cancel bar (Figma); the shell hides the navigation suite.
            LoginScreen(platform = route.platform, onDone = navigator::back, onCancel = navigator::back)
        }
    }

private fun Navigator.openPlayer(stream: Stream) {
    push(
        PlayerRoute(
            platform = stream.channel.platform,
            login = stream.channel.login,
            title = stream.title,
            thumbnailUrl = stream.thumbnailUrl,
        ),
    )
}

@Composable
private fun BrowseTab(navigator: Navigator) {
    BrowseEntry(
        onOpenPlayer = navigator::openPlayer,
        onOpenChannel = { ref -> navigator.push(ChannelRoute(ref.platform, ref.login)) },
        onOpenCategory = { category -> navigator.push(CategoryRoute(category.platform, category.id, category.name)) },
    )
}

@Composable
private fun CategoryPage(
    route: CategoryRoute,
    navigator: Navigator,
) {
    CategoryEntry(
        platform = route.platform,
        categoryId = route.id,
        name = route.name,
        onOpenPlayer = navigator::openPlayer,
        onOpenChannel = { ref -> navigator.push(ChannelRoute(ref.platform, ref.login)) },
        onBack = navigator::back,
    )
}

@Composable
private fun FollowingTab(navigator: Navigator) {
    FollowingEntry(
        onOpenPlayer = navigator::openPlayer,
        onOpenChannel = { ref -> navigator.push(ChannelRoute(ref.platform, ref.login)) },
        onSignIn = { platform -> navigator.push(LoginRoute(platform)) },
        onOpenBrowse = { navigator.select(TopLevel.BROWSE) },
    )
}

@Composable
private fun SearchTab(navigator: Navigator) {
    SearchEntry(
        onOpenChannel = { ref -> navigator.push(ChannelRoute(ref.platform, ref.login)) },
        onOpenCategory = { category -> navigator.push(CategoryRoute(category.platform, category.id, category.name)) },
    )
}

@Composable
private fun ChannelPage(
    route: ChannelRoute,
    navigator: Navigator,
) {
    ChannelEntry(
        platform = route.platform,
        login = route.login,
        onOpenPlayer = navigator::openPlayer,
        onSignIn = { platform -> navigator.push(LoginRoute(platform)) },
        onBack = navigator::back,
    )
}

@Composable
private fun TabPage(
    tab: TopLevel,
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LurkTopBar(title = stringResource(tab.label), onAccount = navigator::openSettings)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun PushedPage(
    title: String,
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LurkTopBar(
            title = title,
            onAccount = navigator::openSettings,
            navigationIcon = { BackButton(navigator::back) },
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
    }
}

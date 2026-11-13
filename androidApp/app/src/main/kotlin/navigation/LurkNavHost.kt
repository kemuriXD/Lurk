package io.github.kemurixd.lurk.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.LurkTopBar
import io.github.kemurixd.lurk.featureui.browse.BrowseEntry
import io.github.kemurixd.lurk.featureui.browse.CategoryEntry
import io.github.kemurixd.lurk.featureui.channel.ChannelEntry
import io.github.kemurixd.lurk.featureui.following.FollowingEntry
import io.github.kemurixd.lurk.featureui.search.SearchEntry
import io.github.kemurixd.lurk.player.PlayerPlaceholderScreen
import io.github.kemurixd.lurk.designsystem.R as DesignR

/** The Navigation 3 display. Routes whose screen hasn't landed yet show a placeholder [EmptyState]. */
@Composable
fun LurkNavHost(
    backStack: NavBackStack<NavKey>,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::back,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<FollowingRoute> {
                    TabPage(TopLevel.FOLLOWING, navigator) { FollowingTab(navigator) }
                }
                entry<BrowseRoute> {
                    TabPage(TopLevel.BROWSE, navigator) { BrowseTab(navigator) }
                }
                entry<SearchRoute> {
                    TabPage(TopLevel.SEARCH, navigator) { SearchTab(navigator) }
                }
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
                        Placeholder(stringResource(R.string.placeholder_settings))
                    }
                }
                entry<LoginRoute> { route ->
                    val platform = stringResource(route.platform.label)
                    PushedPage(stringResource(R.string.title_login, platform), navigator) {
                        Placeholder(stringResource(R.string.placeholder_login))
                    }
                }
            },
    )
}

private val Platform.label: Int
    get() =
        when (this) {
            Platform.TWITCH -> DesignR.string.platform_twitch
            Platform.KICK -> DesignR.string.platform_kick
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

@Composable
private fun Placeholder(body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title = stringResource(R.string.placeholder_title), body = body, action = null)
    }
}

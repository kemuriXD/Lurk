package io.github.kemurixd.lurk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries

internal const val TAB_METADATA = "lurk.tab"

/**
 * The entry for [route] at [index] in [tab]'s stack. A scene hands over only its entries, whose key is private, so the
 * owning tab rides along in the metadata. The content key names the tab and the place as well as the route, so the
 * same channel open in two tabs, or twice in one, gets its own saved state and ViewModels each time.
 */
internal fun tabEntry(
    tab: TopLevel,
    index: Int,
    route: NavKey,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): NavEntry<NavKey> {
    val entry = entryProvider(route)
    return NavEntry(
        key = route,
        contentKey = "${tab.name}/$index/${entry.contentKey}",
        metadata = entry.metadata + (TAB_METADATA to tab),
    ) { entry.Content() }
}

/** [tab]'s entries, decorated for as long as they stay on its [stack]. */
@Composable
internal fun rememberTabEntries(
    tab: TopLevel,
    stack: List<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>>,
    routes: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val keys = stack.toList()
    val entries = remember(tab, keys) { keys.mapIndexed { index, route -> tabEntry(tab, index, route, routes) } }
    return rememberDecoratedNavEntries(entries, decorators)
}

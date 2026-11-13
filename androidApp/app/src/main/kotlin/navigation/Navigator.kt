package io.github.kemurixd.lurk.navigation

import androidx.navigation3.runtime.NavKey

/** Every move the screens can ask for. Only the app knows the back stack. [start] is the tab the app opened on. */
class Navigator(
    private val backStack: MutableList<NavKey>,
    private val start: TopLevel,
) {
    /**
     * Shows [tab] from its root. Picking the current tab again also returns to its root. Any other tab sits above the
     * start tab's root, so back from it returns to the start tab before leaving the app.
     */
    fun select(tab: TopLevel) {
        val roots = if (tab == start) listOf(start.route) else listOf(start.route, tab.route)
        // Add first, so the stack is never empty in between.
        backStack.addAll(roots)
        backStack.subList(0, backStack.size - roots.size).clear()
    }

    fun push(route: NavKey) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openSettings() = push(SettingsRoute)
}

package io.github.kemurixd.lurk.navigation

import androidx.navigation3.runtime.NavKey

/** Every move the screens can ask for. Only the app knows the back stack. */
class Navigator(
    private val backStack: MutableList<NavKey>,
) {
    /** Shows [tab] from its root. Picking the current tab again also returns to its root. */
    fun select(tab: TopLevel) {
        // Add first, so the stack is never empty in between.
        backStack.add(tab.route)
        backStack.subList(0, backStack.lastIndex).clear()
    }

    fun push(route: NavKey) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openSettings() = push(SettingsRoute)
}

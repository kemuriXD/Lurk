package io.github.kemurixd.lurk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Every move the screens can ask for. Only the app knows the back stacks.
 *
 * Each tab keeps its own stack, as YouTube and Instagram do: switching tabs shows the other tab's stack as it was
 * left, and back pops the tab in front. At the root of any tab but the start tab, back shows the start tab, again as
 * it was left. At the start tab's root there is nothing to go back to, so the system takes the back press.
 *
 * Each stack's first key is its tab's root, and the stacks never empty. The start tab, the tab in front and every
 * stack are snapshot state, so the shell redraws when they change.
 */
class Navigator(
    private val stacks: Map<TopLevel, MutableList<NavKey>>,
    private val startTab: MutableState<TopLevel>,
    private val currentTab: MutableState<TopLevel> = mutableStateOf(startTab.value),
) {
    /** A navigator with every tab at its root, showing [start]. */
    constructor(start: TopLevel) : this(
        TopLevel.entries.associateWith { mutableStateListOf(it.route) },
        mutableStateOf(start),
    )

    /** The tab back returns to before the app closes: Following when signed in, Browse when not. */
    val start: TopLevel get() = startTab.value

    /** The tab in front. */
    val current: TopLevel get() = currentTab.value

    fun stack(tab: TopLevel): List<NavKey> = stacks.getValue(tab)

    val currentStack: List<NavKey> get() = stack(current)

    /** False only at the start tab's root, where back belongs to the system. */
    val canGoBack: Boolean get() = currentStack.size > 1 || current != start

    /** Shows [tab] with its stack as it was left. Picking the tab already in front returns it to its root. */
    fun select(tab: TopLevel) {
        if (tab == current) popToRoot(tab) else currentTab.value = tab
    }

    fun push(route: NavKey) {
        val stack = stacks.getValue(current)
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun back() {
        val stack = stacks.getValue(current)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            current != start -> currentTab.value = start
        }
    }

    fun openSettings() = push(SettingsRoute)

    /**
     * Makes [tab] the start tab, after a sign-in or sign-out. The tab in front keeps its stack, so the page the user
     * is on (often Settings or the login) stays put. Every other tab returns to its root, since what it showed
     * belonged to the old sign-in state.
     */
    fun changeStart(tab: TopLevel) {
        if (tab == start) return
        TopLevel.entries.filter { it != current }.forEach(::popToRoot)
        startTab.value = tab
    }

    private fun popToRoot(tab: TopLevel) {
        val stack = stacks.getValue(tab)
        if (stack.size > 1) stack.subList(1, stack.size).clear()
    }
}

/**
 * A [Navigator] whose stacks, start tab and tab in front are all saved, so they survive rotation and process death.
 * [start] seeds a fresh navigator only; a restored one keeps the start tab it saved.
 */
@Composable
fun rememberNavigator(start: TopLevel): Navigator {
    val stacks = TopLevel.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.route) } }
    val startTab = rememberSaveable { mutableStateOf(start) }
    val currentTab = rememberSaveable { mutableStateOf(start) }
    return remember(startTab, currentTab) { Navigator(stacks, startTab, currentTab) }
}

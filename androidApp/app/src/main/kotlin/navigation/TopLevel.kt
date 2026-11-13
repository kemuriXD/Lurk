package io.github.kemurixd.lurk.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.kemurixd.lurk.R
import io.github.kemurixd.lurk.designsystem.R as DesignR

/** The three destinations in the navigation bar or rail (spec §2.1). */
enum class TopLevel(
    val route: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    FOLLOWING(FollowingRoute, R.string.tab_following, DesignR.drawable.ic_favorite),
    BROWSE(BrowseRoute, R.string.tab_browse, R.drawable.ic_nav_browse),
    SEARCH(SearchRoute, R.string.tab_search, R.drawable.ic_nav_search),
    ;

    companion object {
        /** The tab that owns a back stack: its first key is always a tab root. */
        fun of(backStack: List<NavKey>): TopLevel? = entries.firstOrNull { it.route == backStack.firstOrNull() }
    }
}

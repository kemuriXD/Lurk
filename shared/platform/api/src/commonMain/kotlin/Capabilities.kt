package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.model.CategorySort

/**
 * What a platform can do. Feature code checks these instead of the platform id.
 *
 * [categorySorts] are the category page's sort options in the platform's own order. The ones in
 * [categorySortsNeedingSignIn] work only when signed in. [categorySortDefaults] is the order of preference for the
 * starting sort: the first one the user can use wins.
 *
 * [lastLiveLookup] means the platform's followed and search replies don't say when an offline channel was last live,
 * so screens ask `StreamingPlatform.lastLive` per row instead.
 */
data class Capabilities(
    val follows: Boolean,
    val followMutation: Boolean,
    val chatSend: Boolean,
    val chatHistory: Boolean,
    val cheermotes: Boolean,
    val categorySorts: List<CategorySort>,
    val categorySortsNeedingSignIn: Set<CategorySort>,
    val categorySortDefaults: List<CategorySort>,
    val lastLiveLookup: Boolean = false,
) {
    /** The sort options to offer, in order. */
    fun categorySortOptions(signedIn: Boolean): List<CategorySort> =
        if (signedIn) categorySorts else categorySorts.filterNot { it in categorySortsNeedingSignIn }

    /** The sort a category page starts on. */
    fun defaultCategorySort(signedIn: Boolean): CategorySort {
        val options = categorySortOptions(signedIn)
        return categorySortDefaults.firstOrNull { it in options } ?: options.first()
    }
}

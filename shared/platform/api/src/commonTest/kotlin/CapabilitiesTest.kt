package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.core.model.CategorySort
import kotlin.test.Test
import kotlin.test.assertEquals

class CapabilitiesTest {
    private val caps =
        Capabilities(
            follows = true,
            followMutation = true,
            chatSend = true,
            chatHistory = true,
            cheermotes = false,
            categorySorts = listOf(CategorySort.FEATURED, CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH),
            categorySortsNeedingSignIn = setOf(CategorySort.RECOMMENDED),
            categorySortDefaults = listOf(CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH),
        )

    @Test
    fun signedOutHidesSortsThatNeedSignIn() {
        assertEquals(
            listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH),
            caps.categorySortOptions(signedIn = false),
        )
        assertEquals(caps.categorySorts, caps.categorySortOptions(signedIn = true))
    }

    @Test
    fun defaultIsTheFirstPreferredSortTheUserCanUse() {
        assertEquals(CategorySort.RECOMMENDED, caps.defaultCategorySort(signedIn = true))
        assertEquals(CategorySort.VIEWERS_HIGH, caps.defaultCategorySort(signedIn = false))
    }

    @Test
    fun defaultFallsBackToTheFirstOption() {
        val noDefaults = caps.copy(categorySortDefaults = emptyList())
        assertEquals(CategorySort.FEATURED, noDefaults.defaultCategorySort(signedIn = false))
    }
}

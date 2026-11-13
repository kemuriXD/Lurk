package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    private val harness = BrowseTestHarness()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(harness.dispatcher)
    }

    @AfterTest
    fun tearDown() {
        harness.store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(): CategoriesViewModel =
        harness.create(CategoriesViewModel::class) {
            CategoriesViewModel(listOf(twitch, kick), TestDispatchers(harness.dispatcher))
        }

    private fun c(
        platform: Platform,
        id: String,
    ) = Category(platform, id, "Category $id", boxArtUrl = null)

    @Test
    fun startsOnTwitchAndLoadsOnlyIt() =
        runTest {
            twitch.categories = { pageOf(c(Platform.TWITCH, "1"), c(Platform.TWITCH, "2")) }
            val state = watch(viewModel().state)

            assertEquals(Platform.TWITCH, state.value.platform)
            assertEquals(listOf("1", "2"), state.value.categories.map { it.id })
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slice)
            assertEquals(0, kick.categoriesCalls)
        }

    @Test
    fun categoriesSwitchPlatformLoadsThatPlatform() =
        runTest {
            twitch.categories = { pageOf(c(Platform.TWITCH, "1")) }
            kick.categories = { pageOf(c(Platform.KICK, "9")) }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(CategoriesIntent.SelectPlatform(Platform.KICK))
            runCurrent()

            assertEquals(Platform.KICK, state.value.platform)
            assertEquals(listOf("9"), state.value.categories.map { it.id })
            assertEquals(1, kick.categoriesCalls)

            vm.onIntent(CategoriesIntent.SelectPlatform(Platform.TWITCH))
            runCurrent()
            assertEquals(listOf("1"), state.value.categories.map { it.id })
            assertEquals(1, twitch.categoriesCalls)
        }

    @Test
    fun loadMoreAppendsTheNextPage() =
        runTest {
            twitch.categories = { cursor ->
                if (cursor == null) pageOf(c(Platform.TWITCH, "1"), next = "p2") else pageOf(c(Platform.TWITCH, "2"))
            }
            val vm = viewModel()
            val state = watch(vm.state)
            assertTrue(state.value.canLoadMore)

            vm.onIntent(CategoriesIntent.LoadMore)
            runCurrent()

            assertEquals(listOf("1", "2"), state.value.categories.map { it.id })
            assertFalse(state.value.canLoadMore)
        }

    @Test
    fun failedFirstPageRetries() =
        runTest {
            twitch.categories = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slice)

            twitch.categories = { pageOf(c(Platform.TWITCH, "1")) }
            vm.onIntent(CategoriesIntent.Retry)
            runCurrent()

            assertEquals(listOf("1"), state.value.categories.map { it.id })
        }

    @Test
    fun loadMoreFailureKeepsCategoriesAndRecordsTheError() =
        runTest {
            twitch.categories = { cursor ->
                if (cursor == null) {
                    pageOf(c(Platform.TWITCH, "1"), next = "p2")
                } else {
                    Outcome.Failure(LurkError.Timeout)
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(CategoriesIntent.LoadMore)
            runCurrent()

            assertEquals(listOf("1"), state.value.categories.map { it.id })
            assertEquals(LurkError.Timeout, state.value.loadMoreError)
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slice)
        }

    @Test
    fun nonRetryableLoadMoreFailureStopsPagingAndKeepsItems() =
        runTest {
            var requests = 0
            twitch.categories = { cursor ->
                requests++
                if (cursor == null) {
                    pageOf(c(Platform.TWITCH, "1"), next = "p2")
                } else {
                    Outcome.Failure(LurkError.IntegrityRequired)
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(CategoriesIntent.LoadMore)
            runCurrent()
            assertEquals(2, requests)
            assertEquals(LurkError.IntegrityRequired, state.value.loadMoreError)
            assertEquals(listOf("1"), state.value.categories.map { it.id })

            vm.onIntent(CategoriesIntent.LoadMore)
            vm.onIntent(CategoriesIntent.Retry)
            runCurrent()

            assertEquals(2, requests)
            assertEquals(listOf("1"), state.value.categories.map { it.id })
        }

    @Test
    fun retryableLoadMoreFailureRetriesWithTheSameCursor() =
        runTest {
            val cursors = mutableListOf<String?>()
            twitch.categories = { cursor ->
                cursors += cursor
                when {
                    cursor == null -> pageOf(c(Platform.TWITCH, "1"), next = "p2")
                    cursors.size == 2 -> Outcome.Failure(LurkError.Timeout)
                    else -> pageOf(c(Platform.TWITCH, "2"))
                }
            }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(CategoriesIntent.LoadMore)
            runCurrent()
            vm.onIntent(CategoriesIntent.Retry)
            runCurrent()

            assertEquals(listOf(null, "p2", "p2"), cursors)
            assertEquals(listOf("1", "2"), state.value.categories.map { it.id })
        }
}

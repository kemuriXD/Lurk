package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testStream
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryStreamsViewModelTest {
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

    private fun viewModel(
        platform: Platform = Platform.KICK,
        id: String = "42",
    ): CategoryStreamsViewModel =
        harness.create(CategoryStreamsViewModel::class) {
            CategoryStreamsViewModel(platform, id, listOf(twitch, kick), TestDispatchers(harness.dispatcher))
        }

    private fun k(
        login: String,
        viewers: Long,
    ) = testStream(Platform.KICK, login = login, viewers = viewers, displayName = login)

    @Test
    fun loadsThePlatformsCategorySortedByViewers() =
        runTest {
            kick.liveInCategory = { id, _ -> if (id == "42") pageOf(k("a", 10), k("b", 30)) else pageOf() }
            val state = watch(viewModel().state)

            assertEquals(listOf(30L, 10L), state.value.streams.map { it.viewers })
            assertEquals(0, twitch.liveInCategoryCalls)
        }

    @Test
    fun loadMoreAppendsASortedBatchBelow() =
        runTest {
            kick.liveInCategory = { _, cursor ->
                if (cursor == null) pageOf(k("a", 50), k("b", 40), next = "p2") else pageOf(k("c", 20), k("d", 60))
            }
            val vm = viewModel()
            val state = watch(vm.state)
            assertTrue(state.value.canLoadMore)

            vm.onIntent(CategoryStreamsIntent.LoadMore)
            runCurrent()

            assertEquals(listOf(50L, 40L, 60L, 20L), state.value.streams.map { it.viewers })
        }

    @Test
    fun categoryWithBlankIdShowsNotFound() =
        runTest {
            kick.liveInCategory = { _, _ -> Outcome.Failure(LurkError.NotFound) }
            val state = watch(viewModel(id = "").state)

            assertEquals(PlatformSlice.Failed(LurkError.NotFound), state.value.slice)
        }

    @Test
    fun notFoundFromThePlatformShowsNotFound() =
        runTest {
            kick.liveInCategory = { _, _ -> Outcome.Failure(LurkError.NotFound) }
            val state = watch(viewModel().state)

            assertEquals(PlatformSlice.Failed(LurkError.NotFound), state.value.slice)
            assertEquals(1, kick.liveInCategoryCalls)
        }

    @Test
    fun retryReloadsAfterAFailure() =
        runTest {
            kick.liveInCategory = { _, _ -> Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)

            kick.liveInCategory = { _, _ -> pageOf(k("a", 10)) }
            vm.onIntent(CategoryStreamsIntent.Retry)
            runCurrent()

            assertEquals(listOf(10L), state.value.streams.map { it.viewers })
        }
}

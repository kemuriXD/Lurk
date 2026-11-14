package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.CategorySort
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeProfileFetcher
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.InMemorySecureStore
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val fetchers = Platform.entries.map { FakeProfileFetcher(it) }
    private var sessions: SessionRepository? = null

    private fun TestScope.sessions(): SessionRepository =
        sessions ?: SessionRepository(InMemorySecureStore(), fetchers, backgroundScope).also { sessions = it }

    private fun TestScope.viewModel(
        platform: Platform = Platform.KICK,
        id: String = "42",
    ): CategoryStreamsViewModel {
        val accounts = AccountsState(sessions())
        return harness.create(CategoryStreamsViewModel::class) {
            CategoryStreamsViewModel(platform, id, listOf(twitch, kick), accounts, TestDispatchers(harness.dispatcher))
        }
    }

    private fun t(
        login: String,
        viewers: Long,
    ) = testStream(Platform.TWITCH, login = login, viewers = viewers, displayName = login)

    private fun k(
        login: String,
        viewers: Long,
    ) = testStream(Platform.KICK, login = login, viewers = viewers, displayName = login)

    @Test
    fun signedOutKickDefaultsToViewersSoItLoadsTheCategorySortedByViewers() =
        runTest {
            kick.liveInCategory = { id, _, _ -> if (id == "42") pageOf(k("a", 10), k("b", 30)) else pageOf() }
            val state = watch(viewModel().state)

            assertEquals(listOf(30L, 10L), state.value.streams.map { it.viewers })
            assertEquals(0, twitch.liveInCategoryCalls)
        }

    @Test
    fun loadMoreAppendsASortedBatchBelow() =
        runTest {
            kick.liveInCategory = { _, cursor, _ ->
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
            kick.liveInCategory = { _, _, _ -> Outcome.Failure(LurkError.NotFound) }
            val state = watch(viewModel(id = "").state)

            assertEquals(PlatformSlice.Failed(LurkError.NotFound), state.value.slice)
        }

    @Test
    fun notFoundFromThePlatformShowsNotFound() =
        runTest {
            kick.liveInCategory = { _, _, _ -> Outcome.Failure(LurkError.NotFound) }
            val state = watch(viewModel().state)

            assertEquals(PlatformSlice.Failed(LurkError.NotFound), state.value.slice)
            assertEquals(1, kick.liveInCategoryCalls)
        }

    @Test
    fun retryReloadsAfterAFailure() =
        runTest {
            kick.liveInCategory = { _, _, _ -> Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)

            kick.liveInCategory = { _, _, _ -> pageOf(k("a", 10)) }
            vm.onIntent(CategoryStreamsIntent.Retry)
            runCurrent()

            assertEquals(listOf(10L), state.value.streams.map { it.viewers })
        }

    @Test
    fun twitchOffersItsSortsAndStartsOnRecommended() =
        runTest {
            val asked = mutableListOf<CategorySort>()
            twitch.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel(Platform.TWITCH).state)

            assertEquals(
                listOf(
                    CategorySort.RECOMMENDED,
                    CategorySort.VIEWERS_HIGH,
                    CategorySort.VIEWERS_LOW,
                    CategorySort.RECENT,
                ),
                state.value.sorts,
            )
            assertEquals(CategorySort.RECOMMENDED, state.value.sort)
            assertEquals(listOf(CategorySort.RECOMMENDED), asked)
        }

    @Test
    fun kickSignedOutHidesRecommendedAndStartsOnViewers() =
        runTest {
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            assertEquals(listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH), state.value.sorts)
            assertEquals(CategorySort.VIEWERS_HIGH, state.value.sort)
            assertEquals(listOf(CategorySort.VIEWERS_HIGH), asked)
        }

    @Test
    fun kickSignedInStartsOnRecommended() =
        runTest {
            sessions().signIn(Platform.KICK, TOKEN)
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            assertEquals(
                listOf(CategorySort.FEATURED, CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH),
                state.value.sorts,
            )
            assertEquals(CategorySort.RECOMMENDED, state.value.sort)
            assertEquals(listOf(CategorySort.RECOMMENDED), asked)
        }

    @Test
    fun switchingTheSortRefetchesTheFirstPageWithIt() =
        runTest {
            val asked = mutableListOf<Pair<String?, CategorySort>>()
            twitch.liveInCategory = { _, cursor, sort ->
                asked += cursor to sort
                if (sort == CategorySort.RECENT) pageOf(t("new", 1)) else pageOf(t("a", 10), next = "p2")
            }
            val vm = viewModel(Platform.TWITCH)
            val state = watch(vm.state)

            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.RECENT))
            runCurrent()

            assertEquals(
                listOf<Pair<String?, CategorySort>>(
                    null to CategorySort.RECOMMENDED,
                    null to CategorySort.RECENT,
                ),
                asked,
            )
            assertEquals(CategorySort.RECENT, state.value.sort)
            assertEquals(listOf("new"), state.value.streams.map { it.channel.login })
            assertFalse(state.value.canLoadMore)
        }

    @Test
    fun loadMoreKeepsTheSort() =
        runTest {
            val asked = mutableListOf<Pair<String?, CategorySort>>()
            twitch.liveInCategory = { _, cursor, sort ->
                asked += cursor to sort
                if (cursor == null) pageOf(t("a", 1), next = "p2") else pageOf(t("b", 2))
            }
            val vm = viewModel(Platform.TWITCH)
            watch(vm.state)
            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.VIEWERS_LOW))
            runCurrent()

            vm.onIntent(CategoryStreamsIntent.LoadMore)
            runCurrent()

            assertEquals("p2" to CategorySort.VIEWERS_LOW, asked.last())
        }

    @Test
    fun switchingTheSortDropsAPageStillLoading() =
        runTest {
            val slowPage = CompletableDeferred<Outcome<Page<Stream>>>()
            twitch.liveInCategory = { _, cursor, sort ->
                when {
                    sort == CategorySort.RECENT -> pageOf(t("new", 1))
                    cursor == null -> pageOf(t("a", 10), next = "p2")
                    else -> slowPage.await()
                }
            }
            val vm = viewModel(Platform.TWITCH)
            val state = watch(vm.state)
            vm.onIntent(CategoryStreamsIntent.LoadMore)
            runCurrent()

            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.RECENT))
            runCurrent()
            slowPage.complete(pageOf(t("old", 5)))
            runCurrent()

            assertEquals(listOf("new"), state.value.streams.map { it.channel.login })
            assertFalse(state.value.loadingMore)
        }

    @Test
    fun selectingTheCurrentSortDoesNotReload() =
        runTest {
            val vm = viewModel(Platform.TWITCH)
            watch(vm.state)

            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.RECOMMENDED))
            runCurrent()

            assertEquals(1, twitch.liveInCategoryCalls)
        }

    @Test
    fun sortsThePlatformLacksAreIgnored() =
        runTest {
            val vm = viewModel(Platform.TWITCH)
            val state = watch(vm.state)

            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.FEATURED))
            runCurrent()

            assertEquals(CategorySort.RECOMMENDED, state.value.sort)
            assertEquals(1, twitch.liveInCategoryCalls)
        }

    @Test
    fun onlyViewerSortsReorderAPage() =
        runTest {
            twitch.liveInCategory = { _, _, _ -> pageOf(t("a", 10), t("b", 30)) }
            val vm = viewModel(Platform.TWITCH)
            val state = watch(vm.state)
            assertEquals(listOf(10L, 30L), state.value.streams.map { it.viewers })

            twitch.liveInCategory = { _, _, _ -> pageOf(t("a", 30), t("b", 10)) }
            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.VIEWERS_LOW))
            runCurrent()
            assertEquals(listOf(10L, 30L), state.value.streams.map { it.viewers })

            twitch.liveInCategory = { _, _, _ -> pageOf(t("a", 10), t("b", 30)) }
            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.VIEWERS_HIGH))
            runCurrent()
            assertEquals(listOf(30L, 10L), state.value.streams.map { it.viewers })
        }

    @Test
    fun signingOutOnRecommendedFallsBackToTheDefault() =
        runTest {
            sessions().signIn(Platform.KICK, TOKEN)
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            sessions().signOut(Platform.KICK)
            runCurrent()

            assertEquals(CategorySort.VIEWERS_HIGH, state.value.sort)
            assertEquals(listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH), state.value.sorts)
            assertEquals(listOf(CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH), asked)
        }

    @Test
    fun aPickedSortSurvivesSigningIn() =
        runTest {
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val vm = viewModel()
            val state = watch(vm.state)
            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.FEATURED))
            runCurrent()

            sessions().signIn(Platform.KICK, TOKEN)
            runCurrent()

            assertEquals(CategorySort.FEATURED, state.value.sort)
            assertTrue(CategorySort.RECOMMENDED in state.value.sorts)
            assertEquals(listOf(CategorySort.VIEWERS_HIGH, CategorySort.FEATURED), asked)
        }

    private suspend fun expireKick() {
        fetchers.first { it.platform == Platform.KICK }.result = Outcome.Failure(LurkError.AuthExpired)
        sessions!!.reportUnauthorized(Platform.KICK)
    }

    @Test
    fun anExpiredKickSessionDoesNotOfferRecommendedAndStartsOnViewers() =
        runTest {
            sessions().signIn(Platform.KICK, TOKEN)
            expireKick()
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            assertEquals(listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH), state.value.sorts)
            assertEquals(CategorySort.VIEWERS_HIGH, state.value.sort)
            assertEquals(listOf(CategorySort.VIEWERS_HIGH), asked)
        }

    @Test
    fun theSessionExpiringMidwayFallsBackToTheDefaultAndReloads() =
        runTest {
            sessions().signIn(Platform.KICK, TOKEN)
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            expireKick()
            runCurrent()

            assertEquals(CategorySort.VIEWERS_HIGH, state.value.sort)
            assertEquals(listOf(CategorySort.FEATURED, CategorySort.VIEWERS_HIGH), state.value.sorts)
            assertEquals(listOf(CategorySort.RECOMMENDED, CategorySort.VIEWERS_HIGH), asked)
        }

    @Test
    fun signingInWithNoPickReloadsAsRecommended() =
        runTest {
            val asked = mutableListOf<CategorySort>()
            kick.liveInCategory = { _, _, sort ->
                asked += sort
                pageOf()
            }
            val state = watch(viewModel().state)

            sessions().signIn(Platform.KICK, TOKEN)
            runCurrent()

            assertEquals(CategorySort.RECOMMENDED, state.value.sort)
            assertEquals(listOf(CategorySort.VIEWERS_HIGH, CategorySort.RECOMMENDED), asked)
        }

    @Test
    fun switchingTheSortNeverShowsTheOldPagesUnderTheNewSort() =
        runTest {
            twitch.liveInCategory = { _, _, sort ->
                if (sort == CategorySort.RECENT) pageOf(t("new", 1)) else pageOf(t("old", 10))
            }
            val vm = viewModel(Platform.TWITCH)
            val seen = mutableListOf<CategoryStreamsUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }
            runCurrent()

            vm.onIntent(CategoryStreamsIntent.SelectSort(CategorySort.RECENT))
            runCurrent()

            val underNewSort = seen.filter { it.sort == CategorySort.RECENT }
            assertTrue(underNewSort.isNotEmpty())
            assertTrue(underNewSort.none { s -> s.streams.any { it.channel.login == "old" } })
        }

    private companion object {
        const val TOKEN = "stand-in-token"
    }

    @Test
    fun aRetryKeepsTheFailedSliceOnScreenAndMarksItRetrying() =
        runTest {
            kick.liveInCategory = { _, _, _ -> Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            val gate = CompletableDeferred<Unit>()
            kick.liveInCategory = { _, _, _ ->
                gate.await()
                Outcome.Failure(LurkError.Offline)
            }

            vm.onIntent(CategoryStreamsIntent.Retry)
            runCurrent()
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slice)

            gate.complete(Unit)
            runCurrent()
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slice)
            assertFalse(state.value.retrying)
        }
}

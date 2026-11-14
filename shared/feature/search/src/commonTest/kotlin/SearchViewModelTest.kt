package io.github.kemurixd.lurk.feature.search

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Category
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.testing.FakeStreamingPlatform
import io.github.kemurixd.lurk.feature.testing.TestDispatchers
import io.github.kemurixd.lurk.feature.testing.testChannel
import io.github.kemurixd.lurk.feature.testing.testStream
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val twitch = FakeStreamingPlatform(Platform.TWITCH)
    private val kick = FakeStreamingPlatform(Platform.KICK)
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(platforms: List<StreamingPlatform> = listOf(twitch, kick)): SearchViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    SearchViewModel(
                        platforms,
                        TestDispatchers(dispatcher),
                        LastLiveLoader(platforms, TestDispatchers(dispatcher)),
                    )
                }
            }
        return ViewModelProvider.create(store, factory)["vm", SearchViewModel::class]
    }

    private fun TestScope.watch(state: StateFlow<SearchUiState>): StateFlow<SearchUiState> {
        backgroundScope.launch { state.collect {} }
        runCurrent()
        return state
    }

    private fun found(
        vararg channels: Channel,
        categories: List<Category> = emptyList(),
    ): Outcome<SearchResults> = Outcome.Success(SearchResults(channels.toList(), categories))

    private fun channel(
        platform: Platform,
        login: String,
        live: Boolean = false,
    ): Channel =
        testChannel(
            platform,
            login = login,
            displayName = login,
            live = if (live) testStream(platform, login = login) else null,
        )

    private fun List<Channel>.logins() = map { it.ref.login }

    private fun TestScope.type(
        vm: SearchViewModel,
        text: String,
    ) {
        vm.onIntent(SearchIntent.QueryChanged(text))
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Test
    fun startsEmpty() =
        runTest {
            val state = watch(viewModel().state)
            assertEquals(SearchPhase.EMPTY, state.value.phase)
            assertEquals("", state.value.query)
            assertEquals(0, twitch.searchCalls)
        }

    @Test
    fun debounces300ms() =
        runTest {
            val seen = mutableListOf<String>()
            twitch.search = {
                seen += it
                found()
            }
            val vm = viewModel()
            watch(vm.state)

            vm.onIntent(SearchIntent.QueryChanged("pi"))
            advanceTimeBy(100)
            vm.onIntent(SearchIntent.QueryChanged("pix"))
            advanceTimeBy(DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(0, twitch.searchCalls)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, twitch.searchCalls)
            assertEquals(1, kick.searchCalls)
            assertEquals(listOf("pix"), seen)
        }

    @Test
    fun latestQueryWins() =
        runTest {
            val slowA = CompletableDeferred<Outcome<SearchResults>>()
            val fastAb = CompletableDeferred<Outcome<SearchResults>>()
            twitch.search = { if (it == "a") slowA.await() else fastAb.await() }
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "a")
            type(vm, "ab")
            fastAb.complete(found(channel(Platform.TWITCH, "ab_result")))
            runCurrent()
            slowA.complete(found(channel(Platform.TWITCH, "a_result")))
            runCurrent()

            assertEquals(listOf("ab_result"), state.value.channels.logins())
        }

    @Test
    fun olderRequestIsCancelledNotJustIgnored() =
        runTest {
            var cancelled = false
            twitch.search = {
                if (it == "a") {
                    try {
                        CompletableDeferred<Outcome<SearchResults>>().await()
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                } else {
                    found()
                }
            }
            val vm = viewModel()
            watch(vm.state)

            type(vm, "a")
            type(vm, "ab")

            assertTrue(cancelled)
        }

    @Test
    fun blankQueryClearsWithoutRequest() =
        runTest {
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            assertEquals(SearchPhase.RESULTS, state.value.phase)
            val calls = twitch.searchCalls

            vm.onIntent(SearchIntent.QueryChanged("   "))
            runCurrent()
            advanceTimeBy(DEBOUNCE_MS * 2)
            runCurrent()

            assertEquals(calls, twitch.searchCalls)
            assertEquals(SearchPhase.EMPTY, state.value.phase)
            assertTrue(state.value.channels.isEmpty())
            assertTrue(state.value.slices.isEmpty())
        }

    @Test
    fun clearEmptiesQueryAndResults() =
        runTest {
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")

            vm.onIntent(SearchIntent.Clear)
            runCurrent()

            assertEquals("", state.value.query)
            assertEquals(SearchPhase.EMPTY, state.value.phase)
            assertTrue(state.value.channels.isEmpty())
        }

    @Test
    fun queryIsTrimmed() =
        runTest {
            val seen = mutableListOf<String>()
            twitch.search = {
                seen += it
                found()
            }
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "  pix  ")

            assertEquals(listOf("pix"), seen)
            assertEquals("  pix  ", state.value.query)
        }

    @Test
    fun whitespaceAroundTheSameQueryDoesNotSearchAgain() =
        runTest {
            val vm = viewModel()
            watch(vm.state)
            type(vm, "pix")
            type(vm, "pix ")
            assertEquals(1, twitch.searchCalls)
        }

    @Test
    fun typingPhaseWhileWaitingThenResults() =
        runTest {
            val gate = CompletableDeferred<Outcome<SearchResults>>()
            twitch.search = { gate.await() }
            val vm = viewModel()
            val state = watch(vm.state)

            vm.onIntent(SearchIntent.QueryChanged("pix"))
            runCurrent()
            assertEquals(SearchPhase.TYPING, state.value.phase)
            assertEquals(PlatformSlice.Loading, state.value.slices[Platform.TWITCH])

            advanceTimeBy(DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(SearchPhase.TYPING, state.value.phase)

            gate.complete(found(channel(Platform.TWITCH, "t1")))
            runCurrent()
            assertEquals(SearchPhase.RESULTS, state.value.phase)
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
        }

    @Test
    fun liveChannelsFirst() =
        runTest {
            twitch.search = {
                found(channel(Platform.TWITCH, "t_offline"), channel(Platform.TWITCH, "t_live", live = true))
            }
            kick.search = { found(channel(Platform.KICK, "k_offline"), channel(Platform.KICK, "k_live", live = true)) }
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "pix")

            assertEquals(listOf("t_live", "k_live", "t_offline", "k_offline"), state.value.channels.logins())
        }

    @Test
    fun twitchBeforeKickAmongEqualsWhateverTheBindingOrder() =
        runTest {
            twitch.search =
                { found(channel(Platform.TWITCH, "t_live", live = true), channel(Platform.TWITCH, "t_off")) }
            kick.search = { found(channel(Platform.KICK, "k_live", live = true), channel(Platform.KICK, "k_off")) }
            val vm = viewModel(listOf(kick, twitch))
            val state = watch(vm.state)

            type(vm, "pix")

            assertEquals(listOf("t_live", "k_live", "t_off", "k_off"), state.value.channels.logins())
        }

    @Test
    fun categoriesFromBothPlatformsInPlatformOrder() =
        runTest {
            val tc = Category(Platform.TWITCH, "1", "Retro", null)
            val kc = Category(Platform.KICK, "2", "Retro", null)
            twitch.search = { found(categories = listOf(tc)) }
            kick.search = { found(categories = listOf(kc)) }
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "retro")

            assertEquals(listOf(tc, kc), state.value.categories)
            assertEquals(SearchPhase.RESULTS, state.value.phase)
        }

    @Test
    fun oneFailingPlatformStillShowsOther() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            kick.search = { found(channel(Platform.KICK, "k1")) }
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "pix")

            assertEquals(listOf("k1"), state.value.channels.logins())
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.TWITCH])
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.KICK])
            assertEquals(SearchPhase.RESULTS, state.value.phase)
        }

    @Test
    fun retryReloadsOnlyThatPlatform() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            val kickCalls = kick.searchCalls

            vm.onIntent(SearchIntent.Retry(Platform.TWITCH))
            runCurrent()

            assertEquals(kickCalls, kick.searchCalls)
            assertEquals(listOf("t1"), state.value.channels.logins())
            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
        }

    @Test
    fun retryFinishingAfterANewQueryNeverShows() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            val oldRetry = CompletableDeferred<Outcome<SearchResults>>()
            twitch.search = { if (it == "pix") oldRetry.await() else found(channel(Platform.TWITCH, "new_result")) }

            vm.onIntent(SearchIntent.Retry(Platform.TWITCH))
            runCurrent()
            type(vm, "pixel")
            oldRetry.complete(found(channel(Platform.TWITCH, "old_result")))
            runCurrent()

            assertEquals(listOf("new_result"), state.value.channels.logins())
            assertEquals("pixel", state.value.query)
        }

    @Test
    fun noResultsPhase() =
        runTest {
            val vm = viewModel()
            val state = watch(vm.state)

            type(vm, "zzqxv")

            assertEquals(SearchPhase.NO_RESULTS, state.value.phase)
            assertTrue(state.value.channels.isEmpty())
        }

    @Test
    fun filterIsClientSide() =
        runTest {
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            kick.search = { found(channel(Platform.KICK, "k1")) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            val calls = twitch.searchCalls + kick.searchCalls

            vm.onIntent(SearchIntent.SelectFilter(PlatformFilter.KICK))
            runCurrent()

            assertEquals(listOf("k1"), state.value.channels.logins())
            assertEquals(setOf(Platform.KICK), state.value.slices.keys)
            assertEquals(PlatformFilter.KICK, state.value.filter)
            assertEquals(calls, twitch.searchCalls + kick.searchCalls)

            vm.onIntent(SearchIntent.SelectFilter(PlatformFilter.ALL))
            runCurrent()
            assertEquals(listOf("t1", "k1"), state.value.channels.logins())
        }

    @Test
    fun filteredOutResultsGiveNoResultsPhase() =
        runTest {
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")

            vm.onIntent(SearchIntent.SelectFilter(PlatformFilter.KICK))
            runCurrent()

            assertEquals(SearchPhase.NO_RESULTS, state.value.phase)
        }

    @Test
    fun typeFilterShowsOnlyChannelsOrOnlyCategoriesWithoutSearchingAgain() =
        runTest {
            val game = Category(Platform.TWITCH, "1000003", "Pixel Game", null)
            twitch.search = { found(channel(Platform.TWITCH, "t1"), categories = listOf(game)) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            val calls = twitch.searchCalls + kick.searchCalls

            vm.onIntent(SearchIntent.SelectType(SearchType.CHANNELS))
            runCurrent()
            assertEquals(listOf("t1"), state.value.channels.logins())
            assertEquals(emptyList(), state.value.categories)
            assertEquals(SearchType.CHANNELS, state.value.type)

            vm.onIntent(SearchIntent.SelectType(SearchType.CATEGORIES))
            runCurrent()
            assertEquals(emptyList(), state.value.channels.logins())
            assertEquals(listOf(game), state.value.categories)

            vm.onIntent(SearchIntent.SelectType(SearchType.ALL))
            runCurrent()
            assertEquals(listOf("t1"), state.value.channels.logins())
            assertEquals(listOf(game), state.value.categories)
            assertEquals(calls, twitch.searchCalls + kick.searchCalls)
        }

    @Test
    fun typeFilterWithNothingOfThatTypeGivesNoResultsPhase() =
        runTest {
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")

            vm.onIntent(SearchIntent.SelectType(SearchType.CATEGORIES))
            runCurrent()

            assertEquals(SearchPhase.NO_RESULTS, state.value.phase)
        }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }

    @Test
    fun anOfflineKickResultOnScreenGetsItsLastLiveTime() =
        runTest {
            val then = Instant.parse("2026-01-01T00:00:00Z")
            kick.search = { found(channel(Platform.KICK, "amy"), channel(Platform.KICK, "bob")) }
            kick.lastLive = { Outcome.Success(then) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "a")

            vm.onIntent(SearchIntent.OfflineRowShown(ChannelRef(Platform.KICK, "amy")))
            runCurrent()

            val byLogin = state.value.channels.associateBy { it.ref.login }
            assertEquals(then, byLogin.getValue("amy").lastStream?.startedAt)
            assertEquals(null, byLogin.getValue("bob").lastStream?.startedAt)
            assertEquals(listOf(ChannelRef(Platform.KICK, "amy")), kick.lastLiveRequests)
        }

    @Test
    fun aResultHiddenBeforeItsTurnCostsNoLookup() =
        runTest {
            kick.search = { found(channel(Platform.KICK, "amy")) }
            kick.lastLive = {
                delay(1_000)
                Outcome.Success(Instant.parse("2026-01-01T00:00:00Z"))
            }
            val vm = viewModel()
            watch(vm.state)
            type(vm, "a")

            // Three rows elsewhere hold every permit, so amy has to wait her turn.
            repeat(3) { vm.onIntent(SearchIntent.OfflineRowShown(ChannelRef(Platform.KICK, "busy$it"))) }
            vm.onIntent(SearchIntent.OfflineRowShown(ChannelRef(Platform.KICK, "amy")))
            runCurrent()
            vm.onIntent(SearchIntent.OfflineRowHidden(ChannelRef(Platform.KICK, "amy")))
            advanceTimeBy(5_000)
            runCurrent()

            assertTrue(ChannelRef(Platform.KICK, "amy") !in kick.lastLiveRequests, "fetched: ${kick.lastLiveRequests}")
        }

    @Test
    fun aFailedLastLiveLookupKeepsTheResultOffline() =
        runTest {
            kick.search = { found(channel(Platform.KICK, "amy")) }
            kick.lastLive = { Outcome.Failure(LurkError.Timeout) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "a")

            vm.onIntent(SearchIntent.OfflineRowShown(ChannelRef(Platform.KICK, "amy")))
            runCurrent()

            assertEquals(
                null,
                state.value.channels
                    .single()
                    .lastStream
                    ?.startedAt,
            )
            assertEquals(1, kick.lastLiveRequests.size)
        }

    @Test
    fun aRetryKeepsTheFailureOnScreenAndMarksItRetrying() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            val gate = CompletableDeferred<Outcome<SearchResults>>()
            twitch.search = { gate.await() }

            vm.onIntent(SearchIntent.Retry(Platform.TWITCH))
            runCurrent()

            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.TWITCH])
            assertEquals(setOf(Platform.TWITCH), state.value.retrying)
            assertEquals(SearchPhase.RESULTS, state.value.phase)

            gate.complete(Outcome.Failure(LurkError.Offline))
            runCurrent()
            assertEquals(PlatformSlice.Failed(LurkError.Offline), state.value.slices[Platform.TWITCH])
            assertTrue(state.value.retrying.isEmpty())
            assertEquals(SearchPhase.RESULTS, state.value.phase)
        }

    @Test
    fun aRetryThatSucceedsReplacesTheFailureAndStopsRetrying() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            twitch.search = { found(channel(Platform.TWITCH, "t1")) }

            vm.onIntent(SearchIntent.Retry(Platform.TWITCH))
            runCurrent()

            assertEquals(PlatformSlice.Loaded(Unit), state.value.slices[Platform.TWITCH])
            assertTrue(state.value.retrying.isEmpty())
        }

    @Test
    fun aNewQueryEndsTheRetryMark() =
        runTest {
            twitch.search = { Outcome.Failure(LurkError.Offline) }
            val vm = viewModel()
            val state = watch(vm.state)
            type(vm, "pix")
            twitch.search = { CompletableDeferred<Outcome<SearchResults>>().await() }
            vm.onIntent(SearchIntent.Retry(Platform.TWITCH))
            runCurrent()
            assertEquals(setOf(Platform.TWITCH), state.value.retrying)

            vm.onIntent(SearchIntent.QueryChanged("pixel"))
            runCurrent()

            assertTrue(state.value.retrying.isEmpty())
        }
}
